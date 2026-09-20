using System.ComponentModel.DataAnnotations;
using System.Text.Json;

namespace RentMate.Api.Models;

public enum ChoreRecurrence
{
    Once,
    Weekly,
    Monthly
}

/// <summary>
/// A rotating household chore (US-9). The rotation order is stored as a
/// JSON array of User GUIDs (matches the "String (ordered list of User
/// GUIDs)" field from the Part 1 data model) and advances by one position
/// every time the chore is completed.
/// </summary>
public class Chore
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid HouseholdId { get; set; }
    public Household Household { get; set; } = null!;

    [Required]
    [MaxLength(128)]
    public string Title { get; set; } = string.Empty;

    public ChoreRecurrence Recurrence { get; set; } = ChoreRecurrence.Weekly;

    /// <summary>Points awarded per completion (default 1, can be changed at creation - US-12).</summary>
    public int PointValue { get; set; } = 1;

    /// <summary>JSON-serialised ordered list of member GUIDs, e.g. ["guid1","guid2","guid3"].</summary>
    public string RotationOrderJson { get; set; } = "[]";

    /// <summary>Index into the rotation order of whoever is currently assigned.</summary>
    public int CurrentRotationIndex { get; set; } = 0;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<ChoreCompletion> Completions { get; set; } = new List<ChoreCompletion>();

    public List<Guid> GetRotationOrder() =>
        JsonSerializer.Deserialize<List<Guid>>(RotationOrderJson) ?? new List<Guid>();

    public void SetRotationOrder(List<Guid> order) =>
        RotationOrderJson = JsonSerializer.Serialize(order);

    /// <summary>The member GUID currently responsible for this chore.</summary>
    public Guid? CurrentAssigneeId()
    {
        var order = GetRotationOrder();
        if (order.Count == 0) return null;
        return order[CurrentRotationIndex % order.Count];
    }
}

/// <summary>
/// Records a single completion of a chore, including offline completions
/// (US-7, US-9). Points are awarded per completion and totalled per member
/// per calendar month for the leaderboard (US-12).
/// </summary>
public class ChoreCompletion
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid ChoreId { get; set; }
    public Chore Chore { get; set; } = null!;

    [Required]
    public Guid CompletedByUserId { get; set; }
    public User CompletedByUser { get; set; } = null!;

    /// <summary>Server-recorded completion time.</summary>
    public DateTime CompletedAt { get; set; } = DateTime.UtcNow;

    /// <summary>Device-recorded time, present when the completion happened offline (US-7).</summary>
    public DateTime? OfflineTimestamp { get; set; }

    public int PointsAwarded { get; set; }
}
