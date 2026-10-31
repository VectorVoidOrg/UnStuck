def compute_compensation(delay_days: int, per_day: int | None) -> int:
    """Computes statutory customer compensation.

    Returns 0 if per_day is None or delay_days <= 0, else per_day * delay_days.
    """
    if per_day is None or delay_days <= 0:
        return 0
    return int(per_day * delay_days)

