namespace VedaAxis.Core;

public sealed class TimelineClock
{
    private DateTimeOffset startedAt;
    private TimeSpan correction;

    public bool IsRunning { get; private set; }

    public void Start(DateTimeOffset now)
    {
        startedAt = now;
        correction = TimeSpan.Zero;
        IsRunning = true;
    }

    public void Stop()
    {
        IsRunning = false;
    }

    public long ElapsedMilliseconds(DateTimeOffset now)
    {
        return IsRunning ? (long)(now - startedAt - correction).TotalMilliseconds : 0;
    }

    public void ApplyAnchor(long plannedAtMs, DateTimeOffset observedAt)
    {
        if (!IsRunning)
        {
            Start(observedAt - TimeSpan.FromMilliseconds(plannedAtMs));
            return;
        }

        var observedElapsed = observedAt - startedAt - correction;
        correction += observedElapsed - TimeSpan.FromMilliseconds(plannedAtMs);
    }
}
