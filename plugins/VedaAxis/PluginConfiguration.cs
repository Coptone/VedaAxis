using Dalamud.Configuration;

namespace VedaAxis;

public sealed class PluginConfiguration : IPluginConfiguration
{
    public int Version { get; set; } = 1;

    public bool Enabled { get; set; } = true;

    public string ApiBaseUrl { get; set; } = "http://localhost:8080";

    public string LocalSlot { get; set; } = "H2";

    public string StrategyTag { get; set; } = "O8S-POC";

    public string TrackMode { get; set; } = "EIGHT";

    public float OverlayOpacity { get; set; } = 0.72f;

    public string? DeviceId { get; set; }

    public string? AccessToken { get; set; }

    public string? RefreshToken { get; set; }
}
