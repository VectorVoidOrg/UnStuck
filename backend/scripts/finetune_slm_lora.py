"""
UFO Layer 3 — Small Quantized Model (SLM) 4-Bit QLoRA Fine-Tuning Script
(`backend/scripts/finetune_slm_lora.py`).

Generates an instruction-tuning dataset of PII-redacted Indian UPI scam investigations
(`backend/data/ufo_slm_finetune_dataset.jsonl`) and fine-tunes a 4-bit quantized Small
Language Model (default: `Qwen/Qwen2.5-3B-Instruct` or `google/gemma-2-2b-it` or
`meta-llama/Llama-3.2-3B-Instruct`) using HuggingFace `transformers`, `peft` (LoRA),
and `bitsandbytes` (NF4 quantization).

Usage:
    # Step 1: Generate the JSONL training dataset (runs in <1s, no GPU needed)
    python backend/scripts/finetune_slm_lora.py --generate-only

    # Step 2: Run 4-bit QLoRA fine-tuning on GPU
    python backend/scripts/finetune_slm_lora.py --base-model Qwen/Qwen2.5-3B-Instruct --epochs 3
"""
import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BACKEND_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BACKEND_DIR / "data"
MODELS_DIR = BACKEND_DIR / "models"


SEED_FINETUNE_EXAMPLES: List[Dict[str, str]] = [
    {
        "instruction": (
            "Analyze the PII-redacted UPI transaction & dispute context and output ONLY valid JSON "
            "matching AgentVerdictSchema."
        ),
        "input": (
            "Amount: ₹4200.0 | ActiveCall: True | Context: Received SMS that electricity bill is unpaid "
            "and power will disconnect at 9:30 PM. Called <PHONE_TOKEN_A1B2C3D4E5> and paid <UPI_VPA_TOKEN_F6E5D4C3B2>."
        ),
        "output": json.dumps({
            "is_scam": True,
            "scam_category": "FAKE_KYC",
            "confidence": 0.96,
            "recommended_action": "AUTO_REFUND",
            "recommended_refund_inr": 4200.0,
            "place_lien": True,
            "rbi_circular_citations": ["RBI/2017-18/15 Clause 7 (Social Engineering & Immediate Lien)"],
            "reasoning_summary": "Urgent utility disconnection threat combined with active phone call to unfamiliar VPA.",
        }),
    },
    {
        "instruction": (
            "Analyze the PII-redacted UPI transaction & dispute context and output ONLY valid JSON "
            "matching AgentVerdictSchema."
        ),
        "input": (
            "Amount: ₹2800.0 | ActiveCall: False | Context: Buyer on OLX sent QR code and asked me to "
            "scan and enter UPI PIN to receive ₹2800 advance payment to <UPI_VPA_TOKEN_1122334455>."
        ),
        "output": json.dumps({
            "is_scam": True,
            "scam_category": "COLLECT_REQUEST_TRAP",
            "confidence": 0.98,
            "recommended_action": "AUTO_REFUND",
            "recommended_refund_inr": 2800.0,
            "place_lien": True,
            "rbi_circular_citations": ["NPCI UPI Pull/Collect Request Consumer Protection Guidelines"],
            "reasoning_summary": "Classic QR scan / Collect Request trap where victim enters UPI PIN thinking they are receiving funds.",
        }),
    },
    {
        "instruction": (
            "Analyze the PII-redacted UPI transaction & dispute context and output ONLY valid JSON "
            "matching AgentVerdictSchema."
        ),
        "input": (
            "Amount: ₹18500.0 | ScreenShare: True | Context: Fake bank helpline asked me to install AnyDesk "
            "to update PAN KYC for account <BANK_AC_TOKEN_9988776655>."
        ),
        "output": json.dumps({
            "is_scam": True,
            "scam_category": "SCREEN_SHARE",
            "confidence": 0.97,
            "recommended_action": "PLACE_LIEN",
            "recommended_refund_inr": 18500.0,
            "place_lien": True,
            "rbi_circular_citations": ["RBI/2017-18/15 Remote Access Fraud & MHA 1930 Lien Protocol"],
            "reasoning_summary": "Remote screen-sharing tool (AnyDesk) used to compromise session; amount > ₹5,000 requires NPCI Lien + HITL refund approval.",
        }),
    },
    {
        "instruction": (
            "Analyze the PII-redacted UPI transaction & dispute context and output ONLY valid JSON "
            "matching AgentVerdictSchema."
        ),
        "input": (
            "Amount: ₹650.0 | ActiveCall: False | Context: Paid regular neighborhood grocery merchant "
            "<UPI_VPA_TOKEN_5544332211> during daytime."
        ),
        "output": json.dumps({
            "is_scam": False,
            "scam_category": "SAFE",
            "confidence": 0.95,
            "recommended_action": "ALLOW_PAYMENT",
            "recommended_refund_inr": 0.0,
            "place_lien": False,
            "rbi_circular_citations": [],
            "reasoning_summary": "Routine low-value payment to established merchant with no adversarial signals.",
        }),
    },
]


def generate_dataset(out_file: Path, multiplier: int = 25) -> Path:
    out_file.parent.mkdir(parents=True, exist_ok=True)
    rows = SEED_FINETUNE_EXAMPLES * multiplier
    with out_file.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"✅ Generated {len(rows)} SLM instruction-tuning samples -> {out_file}")
    return out_file


def run_qlora_finetuning(base_model: str, dataset_path: Path, output_dir: Path, epochs: int) -> None:
    import torch
    from datasets import load_dataset
    from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
    from transformers import (
        AutoModelForCausalLM,
        AutoTokenizer,
        BitsAndBytesConfig,
        TrainingArguments,
        Trainer,
        DataCollatorForLanguageModeling,
    )

    print(f"🚀 Loading base SLM `{base_model}` in 4-bit NF4 quantization...")
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16 if torch.cuda.is_available() else torch.float32,
        bnb_4bit_use_double_quant=True,
    )

    tokenizer = AutoTokenizer.from_pretrained(base_model, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    model = AutoModelForCausalLM.from_pretrained(
        base_model,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=True,
    )
    model = prepare_model_for_kbit_training(model)

    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    raw_ds = load_dataset("json", data_files=str(dataset_path), split="train")

    def _format_and_tokenize(batch):
        texts = [
            f"<|im_start|>system\n{inst}<|im_end|>\n<|im_start|>user\n{inp}<|im_end|>\n<|im_start|>assistant\n{out}<|im_end|>"
            for inst, inp, out in zip(batch["instruction"], batch["input"], batch["output"])
        ]
        return tokenizer(texts, truncation=True, max_length=512, padding="max_length")

    tokenized_ds = raw_ds.map(_format_and_tokenize, batched=True, remove_columns=raw_ds.column_names)

    training_args = TrainingArguments(
        output_dir=str(output_dir),
        num_train_epochs=epochs,
        per_device_train_batch_size=4,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        fp16=torch.cuda.is_available(),
        logging_steps=10,
        save_strategy="epoch",
        report_to="none",
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized_ds,
        data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False),
    )
    trainer.train()

    output_dir.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(str(output_dir))
    tokenizer.save_pretrained(str(output_dir))
    print(f"✅ Saved fine-tuned UFO SLM LoRA adapter to: {output_dir}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Fine-tune UFO Layer 3 Small Quantized Model (SLM)")
    parser.add_argument("--base-model", type=str, default="Qwen/Qwen2.5-3B-Instruct")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--generate-only", action="store_true", help="Only generate the JSONL dataset and exit")
    args = parser.parse_args()

    dataset_path = generate_dataset(DATA_DIR / "ufo_slm_finetune_dataset.jsonl")
    if args.generate_only:
        return

    run_qlora_finetuning(
        base_model=args.base_model,
        dataset_path=dataset_path,
        output_dir=MODELS_DIR / "ufo-slm-lora",
        epochs=args.epochs,
    )


if __name__ == "__main__":
    main()
