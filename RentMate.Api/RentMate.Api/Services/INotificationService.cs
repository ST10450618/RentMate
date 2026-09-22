namespace RentMate.Api.Services;

public enum NotificationCategory
{
    Bills,
    Chores,
    ShoppingList,
    Maintenance
}

public interface INotificationService
{
    /// <summary>
    /// Sends a push notification to a single device token (US-8). deepLink
    /// tells the Android app which screen to open (S4 Bills, S6 Chores or
    /// S9 Maintenance, per the wireflow in the Part 1 planning doc).
    /// </summary>
    Task SendAsync(string fcmToken, NotificationCategory category, string title, string body, string deepLink);
}
