using RentMate.Api.Models;

namespace RentMate.Api.DTOs;

public record CreateChoreRequest(
    string Title,
    ChoreRecurrence Recurrence,
    int PointValue,
    List<Guid> RotationOrder);

public record ChoreForecastEntry(int CycleNumber, Guid AssigneeUserId, string AssigneeDisplayName);

public record ChoreResponse(
    Guid Id,
    Guid HouseholdId,
    string Title,
    ChoreRecurrence Recurrence,
    int PointValue,
    Guid? CurrentAssigneeUserId,
    string? CurrentAssigneeDisplayName,
    List<ChoreForecastEntry> NextFourCycles,
    DateTime CreatedAt);

/// <summary>US-9/US-7: completing a chore, optionally recorded while offline.</summary>
public record CompleteChoreRequest(DateTime? OfflineTimestamp);

public record ChoreCompletionResponse(
    Guid Id,
    Guid ChoreId,
    Guid CompletedByUserId,
    string CompletedByDisplayName,
    DateTime CompletedAt,
    int PointsAwarded);

public record LeaderboardEntry(Guid UserId, string DisplayName, int MonthPoints, int TotalPoints);

public record LeaderboardResponse(int Year, int Month, List<LeaderboardEntry> Entries);
