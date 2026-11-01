def calculate_penalty(
    days_late: int,
    compensation_rate: float = 100.0,
    has_compensation: bool = True
) -> float:
    """
    Calculates compensation: Days late multiplied by Rs 100 where the rule provides it,
    and zero otherwise.
    """
    if not has_compensation or days_late <= 0:
        return 0.0
    return float(days_late * compensation_rate)

