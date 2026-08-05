using Dalamud.Configuration;

namespace VedaAxis;

public sealed class PluginConfiguration : IPluginConfiguration
{
    public int Version { get; set; } = 1;

    public bool Enabled { get; set; } = true;

    public string ApiBaseUrl { get; set; } = "http://localhost:8080";

    public string LocalSlot { get; set; } = "H2";

    public string EncounterId { get; set; } = "c97e8840-1697-476f-a4ac-8c7996df277b";

    public uint TerritoryId { get; set; } = 1363;

    public string StrategyTag { get; set; } = "DMU-LPDU";

    public string TrackMode { get; set; } = "EIGHT";

    public float OverlayOpacity { get; set; } = 0.72f;

    public string? DeviceId { get; set; }

    public string? AccessToken { get; set; }

    public string? RefreshToken { get; set; }
}
