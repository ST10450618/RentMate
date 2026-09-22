using FirebaseAdmin;
using FirebaseAdmin.Messaging;
using Google.Apis.Auth.OAuth2;

namespace RentMate.Api.Services;

/// <summary>
/// Sends push notifications via Firebase Cloud Messaging (US-8). Reminders
/// fire two days before a bill's due date and on the due date itself, when
/// a chore is assigned, and whenever a maintenance request's status
/// changes - honouring each user's per-category toggle from US-3.
///
/// If no Firebase service account is configured yet (see README, "Setting
/// up Firebase push notifications"), this service logs and no-ops instead
/// of throwing, so the rest of the API keeps working while that piece is
/// still being set up.
/// </summary>
public class FcmNotificationService : INotificationService
{
    private readonly ILogger<FcmNotificationService> _logger;
    private readonly bool _isConfigured;

    public FcmNotificationService(IConfiguration configuration, ILogger<FcmNotificationService> logger)
    {
        _logger = logger;

        var credentialsPath = configuration["Firebase:ServiceAccountJsonPath"];
        _isConfigured = !string.IsNullOrWhiteSpace(credentialsPath) && File.Exists(credentialsPath);

        if (_isConfigured && FirebaseApp.DefaultInstance == null)
        {
            FirebaseApp.Create(new AppOptions
            {
                Credential = GoogleCredential.FromFile(credentialsPath)
            });
        }
    }

    public async Task SendAsync(string fcmToken, NotificationCategory category, string title, string body, string deepLink)
    {
        if (!_isConfigured)
        {
            _logger.LogInformation(
                "Firebase is not configured yet - skipping push notification. Would have sent [{Category}] '{Title}' -> {DeepLink}",
                category, title, deepLink);
            return;
        }

        if (string.IsNullOrWhiteSpace(fcmToken))
        {
            _logger.LogInformation("User has no FCM token registered yet - skipping push notification.");
            return;
        }

        var message = new Message
        {
            Token = fcmToken,
            Notification = new Notification { Title = title, Body = body },
            Data = new Dictionary<string, string>
            {
                { "category", category.ToString() },
                { "deepLink", deepLink }
            }
        };

        try
        {
            await FirebaseMessaging.DefaultInstance.SendAsync(message);
        }
        catch (Exception ex)
        {
            // A single failed push should never fail the API request that
            // triggered it (e.g. saving a bill) - log and move on.
            _logger.LogWarning(ex, "Failed to send FCM push notification.");
        }
    }
}
