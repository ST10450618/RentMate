using System.Net;
using System.Net.Http.Json;
using RentMate.Api.DTOs;
using RentMate.Api.Models;
using Xunit;

namespace RentMate.Api.Tests;

public class HouseholdsAndBillsFlowTests : IClassFixture<RentMateApiFactory>
{
    private readonly RentMateApiFactory _factory;

    public HouseholdsAndBillsFlowTests(RentMateApiFactory factory)
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
    public async Task CreateHousehold_ThenJoinWithInviteCode_AddsSecondMember()
    {
        var alice = Guid.NewGuid();
        var bob = Guid.NewGuid();

        var aliceClient = ClientAs(alice);
        var createResponse = await aliceClient.PostAsJsonAsync("/api/households", new CreateHouseholdRequest("Test House"), TestJson.Options);
        createResponse.EnsureSuccessStatusCode();
        var household = await createResponse.Content.ReadFromJsonAsync<HouseholdResponse>(TestJson.Options);

        Assert.NotNull(household);
        Assert.Equal(6, household!.InviteCode.Length);
        Assert.Single(household.Members);

        var bobClient = ClientAs(bob);
        var joinResponse = await bobClient.PostAsJsonAsync("/api/households/join", new JoinHouseholdRequest(household.InviteCode), TestJson.Options);
        joinResponse.EnsureSuccessStatusCode();
        var joinedHousehold = await joinResponse.Content.ReadFromJsonAsync<HouseholdResponse>(TestJson.Options);

        Assert.Equal(2, joinedHousehold!.Members.Count);
    }

    [Fact]
    public async Task JoinHousehold_WithInvalidCode_Returns404()
    {
        var client = ClientAs(Guid.NewGuid());

        var response = await client.PostAsJsonAsync("/api/households/join", new JoinHouseholdRequest("ZZZZZZ"), TestJson.Options);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task CreateBill_WithEqualSplit_CreatesOneShareEachAndSumsToTotal()
    {
        var alice = Guid.NewGuid();
        var bob = Guid.NewGuid();
        var aliceClient = ClientAs(alice);

        var household = await CreateHouseholdWithMembers(aliceClient, bob);

        var billRequest = new CreateBillRequest(
            "Electricity", 100.00m, DateTime.UtcNow.AddDays(7), SplitMethod.Equal,
            new List<BillShareInput> { new(alice, null), new(bob, null) });

        var response = await aliceClient.PostAsJsonAsync($"/api/households/{household.Id}/bills", billRequest, TestJson.Options);
        response.EnsureSuccessStatusCode();
        var bill = await response.Content.ReadFromJsonAsync<BillResponse>(TestJson.Options);

        Assert.NotNull(bill);
        Assert.Equal(2, bill!.Shares.Count);
        Assert.Equal(100.00m, bill.Shares.Sum(s => s.AmountOwed));
    }

    [Fact]
    public async Task CreateBill_WithZeroAmount_ReturnsBadRequest()
    {
        var alice = Guid.NewGuid();
        var aliceClient = ClientAs(alice);
        var household = await CreateHouseholdWithMembers(aliceClient);

        var billRequest = new CreateBillRequest(
            "Invalid", 0m, DateTime.UtcNow.AddDays(7), SplitMethod.Equal,
            new List<BillShareInput> { new(alice, null) });

        var response = await aliceClient.PostAsJsonAsync($"/api/households/{household.Id}/bills", billRequest, TestJson.Options);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task CreateBill_WithPercentageSharesNotSummingTo100_ReturnsBadRequest()
    {
        var alice = Guid.NewGuid();
        var bob = Guid.NewGuid();
        var aliceClient = ClientAs(alice);
        var household = await CreateHouseholdWithMembers(aliceClient, bob);

        var billRequest = new CreateBillRequest(
            "Rent", 1000m, DateTime.UtcNow.AddDays(7), SplitMethod.Percentage,
            new List<BillShareInput> { new(alice, 60m), new(bob, 30m) }); // sums to 90, not 100

        var response = await aliceClient.PostAsJsonAsync($"/api/households/{household.Id}/bills", billRequest, TestJson.Options);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task SettleUp_AfterOneUnpaidBill_SuggestsSingleTransferToIssuer()
    {
        var alice = Guid.NewGuid();
        var bob = Guid.NewGuid();
        var aliceClient = ClientAs(alice);
        var household = await CreateHouseholdWithMembers(aliceClient, bob);

        var billRequest = new CreateBillRequest(
            "Water", 200m, DateTime.UtcNow.AddDays(7), SplitMethod.Equal,
            new List<BillShareInput> { new(alice, null), new(bob, null) });
        await aliceClient.PostAsJsonAsync($"/api/households/{household.Id}/bills", billRequest, TestJson.Options);

        var settleUpResponse = await aliceClient.GetAsync($"/api/households/{household.Id}/settle-up");
        settleUpResponse.EnsureSuccessStatusCode();
        var settleUp = await settleUpResponse.Content.ReadFromJsonAsync<SettleUpResponse>(TestJson.Options);

        Assert.NotNull(settleUp);
        var suggestion = Assert.Single(settleUp!.Suggestions);
        Assert.Equal(bob, suggestion.FromUserId);
        Assert.Equal(alice, suggestion.ToUserId);
        Assert.Equal(100m, suggestion.Amount); // bob's half of a 200 bill he didn't issue
    }

    [Fact]
    public async Task NonMember_CannotAccessHousehold()
    {
        var alice = Guid.NewGuid();
        var stranger = Guid.NewGuid();
        var aliceClient = ClientAs(alice);
        var household = await CreateHouseholdWithMembers(aliceClient);

        var strangerClient = ClientAs(stranger);
        var response = await strangerClient.GetAsync($"/api/households/{household.Id}");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    private async Task<HouseholdResponse> CreateHouseholdWithMembers(HttpClient creatorClient, params Guid[] otherMembers)
    {
        var createResponse = await creatorClient.PostAsJsonAsync("/api/households", new CreateHouseholdRequest("Test House"), TestJson.Options);
        createResponse.EnsureSuccessStatusCode();
        var household = (await createResponse.Content.ReadFromJsonAsync<HouseholdResponse>(TestJson.Options))!;

        foreach (var memberId in otherMembers)
        {
            var memberClient = _factory.CreateClient();
            memberClient.DefaultRequestHeaders.Add("Test-User-Id", memberId.ToString());
            var joinResponse = await memberClient.PostAsJsonAsync("/api/households/join", new JoinHouseholdRequest(household.InviteCode), TestJson.Options);
            joinResponse.EnsureSuccessStatusCode();
        }

        return household;
    }
}
