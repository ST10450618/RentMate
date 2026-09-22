namespace RentMate.Api.DTOs;

public record CreateHouseholdRequest(string Name);

public record JoinHouseholdRequest(string InviteCode);

public record UpdateLandlordRequest(string? LandlordName, string? LandlordEmail);

public record HouseholdMemberResponse(Guid UserId, string DisplayName, string Email);

public record HouseholdResponse(
    Guid Id,
    string Name,
    string InviteCode,
    string? LandlordName,
    string? LandlordEmail,
    List<HouseholdMemberResponse> Members);

/// <summary>
/// Response for GET /api/households/{id}/sync?since=... - everything that
/// changed in this household after the given timestamp, so the Android
/// client can reconcile its local Room cache after being offline (US-7).
/// </summary>
public record SyncResponse(
    DateTime ServerTime,
    List<BillResponse> UpdatedBills,
    List<ChoreResponse> UpdatedChores,
    List<ShoppingItemResponse> UpdatedShoppingItems,
    List<MaintenanceRequestResponse> UpdatedMaintenanceRequests);
