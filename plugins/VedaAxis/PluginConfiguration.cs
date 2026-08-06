using Dalamud.Configuration;

namespace VedaAxis;

public sealed class PluginConfiguration : IPluginConfiguration
{
    public const int CurrentVersion = 4;
    public const string ProductionApiBaseUrl = "https://coptone.link/VedaAxis";
    public const string LegacyLocalApiBaseUrl = "http://localhost:8080";

    public int Version { get; set; } = CurrentVersion;

    public bool Enabled { get; set; } = true;

    public string ApiBaseUrl { get; set; } = ProductionApiBaseUrl;

    public string LocalSlot { get; set; } = "H2";

    public string StrategyTag { get; set; } = "DMU-P1P2";

    public string TrackMode { get; set; } = "EIGHT";

    public float OverlayOpacity { get; set; } = 0.72f;

    public string OverlayStyle { get; set; } = OverlayPresentation.Strong;

    public string? DeviceId { get; set; }

    public string? AccessToken { get; set; }

    public string? RefreshToken { get; set; }
}
