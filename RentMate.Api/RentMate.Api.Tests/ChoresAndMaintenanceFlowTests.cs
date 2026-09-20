using System.Net.Http.Json;
using RentMate.Api.DTOs;
using RentMate.Api.Models;
using Xunit;

namespace RentMate.Api.Tests;

public class ChoresAndMaintenanceFlowTests : IClassFixture<RentMateApiFactory>
{
    private readonly RentMateApiFactory _factory;

    public ChoresAndMaintenanceFlowTests(RentMateApiFactory factory)
    {
        _factory = factory;
    }

    private HttpClient ClientAs(Guid userId)
    {
        var client = _factory.CreateClient();
        client.DefaultRequestHeaders.Add("Test-User-Id", userId.ToString());
        return client;
    }

    [Fact]
    public async Task CompleteChore_AdvancesRotationToNextMember()
    {
        var alice = Guid.NewGuid();
        var bob = Guid.NewGuid();
        var aliceClient = ClientAs(alice);

        var createHouseholdResponse = await aliceClient.PostAsJsonAsync("/api/households", new CreateHouseholdRequest("Chore House"), TestJson.Options);
        var household = (await createHouseholdResponse.Content.ReadFromJsonAsync<HouseholdResponse>(TestJson.Options))!;

        var bobClient = _factory.CreateClient(); 
        bobClient.DefaultRequestHeaders.Add("Test-User-Id", bob.ToString());
        await bobClient.PostAsJsonAsync("/api/households/join", new JoinHouseholdRequest(household.InviteCode), TestJson.Options);

        var choreRequest = new CreateChoreRequest("Take out bins", ChoreRecurrence.Weekly, 2, new List<Guid> { alice, bob });
        var createChoreResponse = await aliceClient.PostAsJsonAsync($"/api/households/{household.Id}/chores", choreRequest, TestJson.Options);
        createChoreResponse.EnsureSuccessStatusCode();
        var chore = (await createChoreResponse.Content.ReadFromJsonAsync<ChoreResponse>(TestJson.Options))!;

        Assert.Equal(alice, chore.CurrentAssigneeUserId);
        Assert.Equal(4, chore.NextFourCycles.Count);

        var completeResponse = await aliceClient.PostAsJsonAsync($"/api/chores/{chore.Id}/complete", new CompleteChoreRequest(null), TestJson.Options);
        completeResponse.EnsureSuccessStatusCode();
        var completion = await completeResponse.Content.ReadFromJsonAsync<ChoreCompletionResponse>(TestJson.Options);

        Assert.Equal(2, completion!.PointsAwarded);

        var refreshedChoreResponse = await aliceClient.GetAsync($"/api/chores/{chore.Id}");
        var refreshedChore = await refreshedChoreResponse.Content.ReadFromJsonAsync<ChoreResponse>(TestJson.Options);

        Assert.Equal(bob, refreshedChore!.CurrentAssigneeUserId);
    }

    [Fact]
    public async Task SendToLandlord_BatchesOpenRequestsAndMarksThemSent()
    {
        var alice = Guid.NewGuid();
        var aliceClient = ClientAs(alice);

        var createHouseholdResponse = await aliceClient.PostAsJsonAsync("/api/households", new CreateHouseholdRequest("Leaky House"), TestJson.Options);
        var household = (await createHouseholdResponse.Content.ReadFromJsonAsync<HouseholdResponse>(TestJson.Options))!;

        await aliceClient.PutAsJsonAsync($"/api/households/{household.Id}/landlord",
            new UpdateLandlordRequest("Mr Landlord", "landlord@example.com"), TestJson.Options);

        await aliceClient.PostAsJsonAsync($"/api/households/{household.Id}/maintenance",
            new CreateMaintenanceRequest("Leaking tap", "Kitchen tap won't stop dripping", null, MaintenanceCategory.Plumbing, MaintenanceUrgency.Medium),
            TestJson.Options);
        await aliceClient.PostAsJsonAsync($"/api/households/{household.Id}/maintenance",
            new CreateMaintenanceRequest("Broken light", "Hallway light flickers", null, MaintenanceCategory.Electrical, MaintenanceUrgency.Low),
            TestJson.Options);

        var sendResponse = await aliceClient.PostAsync($"/api/households/{household.Id}/maintenance/send-to-landlord", null);
        sendResponse.EnsureSuccessStatusCode();
        var result = await sendResponse.Content.ReadFromJsonAsync<SendToLandlordResponse>(TestJson.Options);

        Assert.Equal(2, result!.RequestsIncluded);
        Assert.Contains("Leaking tap", result.LetterPreview);
        Assert.Contains("Broken light", result.LetterPreview);

        var listResponse = await aliceClient.GetAsync($"/api/households/{household.Id}/maintenance?status=Sent");
        var sentRequests = await listResponse.Content.ReadFromJsonAsync<List<MaintenanceRequestResponse>>(TestJson.Options);

        Assert.Equal(2, sentRequests!.Count);
    }

    [Fact]
    public async Task SendToLandlord_WithNoOpenRequests_ReturnsBadRequest()
    {
        var alice = Guid.NewGuid();
        var aliceClient = ClientAs(alice);

        var createHouseholdResponse = await aliceClient.PostAsJsonAsync("/api/households", new CreateHouseholdRequest("Empty House"), TestJson.Options);
        var household = (await createHouseholdResponse.Content.ReadFromJsonAsync<HouseholdResponse>(TestJson.Options))!;

        var sendResponse = await aliceClient.PostAsync($"/api/households/{household.Id}/maintenance/send-to-landlord", null);

        Assert.Equal(System.Net.HttpStatusCode.BadRequest, sendResponse.StatusCode);
    }
}
