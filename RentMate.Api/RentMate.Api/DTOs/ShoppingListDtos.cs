namespace RentMate.Api.DTOs;

public record AddShoppingItemRequest(string Name);

public record PurchaseShoppingItemRequest(DateTime ClientTimestamp, bool ConvertToBill);

public record ShoppingItemResponse(
    Guid Id,
    Guid HouseholdId,
    string Name,
    Guid AddedByUserId,
    string AddedByDisplayName,
    bool IsPurchased,
    Guid? PurchasedByUserId,
    DateTime? PurchasedAt,
    Guid? ConvertedToBillId,
    DateTime CreatedAt);
