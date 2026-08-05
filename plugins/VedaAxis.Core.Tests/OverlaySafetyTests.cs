namespace VedaAxis.Core.Tests;

public sealed class OverlaySafetyTests
{
    [Theory]
    [InlineData(1, true)]
    [InlineData(16, true)]
    [InlineData(32, true)]
    [InlineData(0, false)]
    [InlineData(-1, false)]
    [InlineData(33, false)]
    [InlineData(long.MaxValue, false)]
    public void ValidatesSlotCountBeforeCreatingSpan(long slotCount, bool expected)
    {
        Assert.Equal(expected, OverlaySafety.IsValidSlotCount(slotCount));
    }

    [Fact]
    public void NormalizesNonFiniteAndOutOfRangeOpacity()
    {
        Assert.Equal(0.72f, OverlaySafety.NormalizeOpacity(float.NaN));
        Assert.Equal(0.72f, OverlaySafety.NormalizeOpacity(float.PositiveInfinity));
        Assert.Equal(0.1f, OverlaySafety.NormalizeOpacity(-5f));
        Assert.Equal(1f, OverlaySafety.NormalizeOpacity(5f));
    }
}
