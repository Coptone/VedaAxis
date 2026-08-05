using System.Net.Http.Json;
using System.Text.Json;
using System.Net.Http.Headers;
using VedaAxis.Core;

namespace VedaAxis;

internal sealed class DeviceAuthorizationClient : IDisposable
{
    private readonly HttpClient httpClient = new() { Timeout = TimeSpan.FromSeconds(15) };
    private readonly JsonSerializerOptions jsonOptions = new(JsonSerializerDefaults.Web);

    public async Task<DeviceAuthorizationResult> AuthorizeAsync(
        string apiBaseUrl,
        Action<string, string> onCode,
        CancellationToken cancellationToken)
    {
        var baseUrl = apiBaseUrl.TrimEnd('/');
        using var createResponse = await httpClient.PostAsJsonAsync(
            $"{baseUrl}/api/v1/device-authorizations",
            new { deviceName = "Dalamud 插件" },
            jsonOptions,
            cancellationToken);
        createResponse.EnsureSuccessStatusCode();
        var deviceCode = await createResponse.Content.ReadFromJsonAsync<DeviceCodeResponse>(jsonOptions, cancellationToken)
                         ?? throw new InvalidDataException("设备授权响应为空");
        onCode(deviceCode.UserCode, deviceCode.ExpiresAt.ToLocalTime().ToString("HH:mm:ss"));

        while (DateTimeOffset.UtcNow < deviceCode.ExpiresAt)
        {
            await Task.Delay(TimeSpan.FromSeconds(Math.Max(2, deviceCode.PollIntervalSeconds)), cancellationToken);
            using var pollResponse = await httpClient.PostAsJsonAsync(
                $"{baseUrl}/api/v1/device-authorizations/token",
                new { deviceCode = deviceCode.DeviceCode },
                jsonOptions,
                cancellationToken);
            pollResponse.EnsureSuccessStatusCode();
            var poll = await pollResponse.Content.ReadFromJsonAsync<DeviceTokenResponse>(jsonOptions, cancellationToken)
                       ?? throw new InvalidDataException("设备轮询响应为空");
            switch (poll.Status)
            {
                case "PENDING":
                    continue;
                case "APPROVED" when poll.DeviceId is not null && poll.Tokens is not null:
                    return new DeviceAuthorizationResult(poll.DeviceId.Value, poll.Tokens);
                case "EXPIRED":
                    throw new TimeoutException("绑定码已过期");
                default:
                    throw new InvalidOperationException($"设备授权状态为 {poll.Status}");
            }
        }

        throw new TimeoutException("绑定码已过期");
    }

    public void Dispose()
    {
        httpClient.Dispose();
    }

    public async Task<TokenPair> RefreshAsync(
        string apiBaseUrl,
        string refreshToken,
        CancellationToken cancellationToken)
    {
        using var response = await httpClient.PostAsJsonAsync(
            $"{apiBaseUrl.TrimEnd('/')}/api/v1/auth/refresh",
            new { refreshToken },
            jsonOptions,
            cancellationToken);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<TokenPair>(jsonOptions, cancellationToken)
               ?? throw new InvalidDataException("刷新令牌响应为空");
    }

    public async Task<PlanSnapshot> MatchPlanAsync(
        string apiBaseUrl,
        string accessToken,
        string encounterId,
        string strategyTag,
        string trackMode,
        CancellationToken cancellationToken)
    {
        var query = $"encounterId={Uri.EscapeDataString(encounterId)}"
                    + $"&strategyTag={Uri.EscapeDataString(strategyTag)}"
                    + $"&trackMode={Uri.EscapeDataString(trackMode)}";
        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            $"{apiBaseUrl.TrimEnd('/')}/api/v1/runtime/plans/match?{query}");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        using var response = await httpClient.SendAsync(request, cancellationToken);
        response.EnsureSuccessStatusCode();
        var result = await response.Content.ReadFromJsonAsync<RuntimePlanResponse>(jsonOptions, cancellationToken)
                     ?? throw new InvalidDataException("运行时计划响应为空");
        return result.Snapshot;
    }

    public async Task UploadExecutionAsync(
        string apiBaseUrl,
        string accessToken,
        FightExecutionBatch batch,
        CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            $"{apiBaseUrl.TrimEnd('/')}/api/v1/fight-executions");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        request.Content = JsonContent.Create(batch, options: jsonOptions);
        using var response = await httpClient.SendAsync(request, cancellationToken);
        response.EnsureSuccessStatusCode();
    }

    private sealed record DeviceCodeResponse(
        string DeviceCode,
        string UserCode,
        DateTimeOffset ExpiresAt,
        int PollIntervalSeconds);

    private sealed record DeviceTokenResponse(string Status, Guid? DeviceId, TokenPair? Tokens);

    private sealed record RuntimePlanResponse(PlanSnapshot Snapshot, DateTimeOffset PublishedAt);
}

internal sealed record DeviceAuthorizationResult(Guid DeviceId, TokenPair Tokens);

internal sealed record TokenPair(
    string AccessToken,
    string RefreshToken,
    string TokenType,
    DateTimeOffset AccessTokenExpiresAt);
