from app.services.penalty import calculate_penalty

def test_founder_case_penalty():
    """
    Founder case hand-check:
    UPI transfer, T+1, reversed on T+9, so 8 days late.
    Penalty: 8 * Rs 100 = Rs 800.
    """
    days_late = 8
    penalty = calculate_penalty(days_late=days_late, compensation_rate=100.0, has_compensation=True)
    assert penalty == 800.0

def test_zero_days_late():
    penalty = calculate_penalty(days_late=0, compensation_rate=100.0, has_compensation=True)
    assert penalty == 0.0

def test_negative_days_late():
    penalty = calculate_penalty(days_late=-3, compensation_rate=100.0, has_compensation=True)
    assert penalty == 0.0

def test_rule_without_compensation():
    penalty = calculate_penalty(days_late=10, compensation_rate=100.0, has_compensation=False)
    assert penalty == 0.0

def test_atm_delay_penalty():
    # 5 days late beyond T+5
    penalty = calculate_penalty(days_late=5, compensation_rate=100.0, has_compensation=True)
    assert penalty == 500.0

