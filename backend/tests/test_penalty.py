import pytest
from app.services.penalty import compute_compensation


@pytest.mark.parametrize(
    "delay_days, per_day, expected",
    [
        (0, 100, 0),
        (-2, 100, 0),
        (3, 100, 300),
        (3, None, 0),
    ],
)
def test_compute_compensation(delay_days, per_day, expected):
    assert compute_compensation(delay_days, per_day) == expected

