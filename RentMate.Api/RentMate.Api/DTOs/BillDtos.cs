using RentMate.Api.Models;

namespace RentMate.Api.DTOs;

/// <summary>
/// One member's share when creating a bill. For SplitMethod.Equal the
/// AmountOrPercentage field is ignored and shares are computed server-side.
/// For Percentage it must be a percentage (all shares summing to 100).
/// For ExactAmount it is a ZAR amount (all shares summing to the bill total).
/// </summary>
public record BillShareInput(Guid UserId, decimal? AmountOrPercentage);

public record CreateBillRequest(
    string Title,
    decimal Amount,
    DateTime DueDate,
    SplitMethod SplitMethod,
    List<BillShareInput> Shares);

public record BillShareResponse(
    Guid Id,
    Guid UserId,
    string DisplayName,
    decimal AmountOwed,
    bool IsPaid,
    DateTime? PaidAt);

public record BillResponse(
    Guid Id,
    Guid HouseholdId,
    string Title,
    decimal Amount,
    DateTime DueDate,
    SplitMethod SplitMethod,
    Guid IssuedByUserId,
    string IssuedByDisplayName,
    DateTime CreatedAt,
    DateTime UpdatedAt,
    List<BillShareResponse> Shares);

/// <summary>
/// US-7: marking a share paid while offline. ClientTimestamp is the
/// device-recorded time; if the record has since been updated on the
/// server with a later timestamp than this, a 409 conflict is returned.
/// </summary>
public record PayBillShareRequest(DateTime ClientTimestamp);

public record SettlementSuggestion(Guid FromUserId, string FromDisplayName, Guid ToUserId, string ToDisplayName, decimal Amount);

public record SettleUpResponse(bool IsProvisional, List<SettlementSuggestion> Suggestions);

public record ConfirmSettlementRequest(Guid ToUserId, decimal Amount);
