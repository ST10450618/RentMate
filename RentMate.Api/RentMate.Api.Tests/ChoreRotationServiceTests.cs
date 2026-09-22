using RentMate.Api.Services;
using Xunit;

namespace RentMate.Api.Tests;

public class ChoreRotationServiceTests
{
    private readonly ChoreRotationService _service = new();

    [Fact]
    public void Forecast_ReturnsRequestedNumberOfCycles()
    {
        var members = new List<Guid> { Guid.NewGuid(), Guid.NewGuid(), Guid.NewGuid() };

        var forecast = _service.Forecast(members, startIndex: 0, cycles: 4);

        Assert.Equal(4, forecast.Count);
    }

    [Fact]
    public void Forecast_WrapsAroundRotationOrder()
    {
        var members = new List<Guid> { Guid.NewGuid(), Guid.NewGuid(), Guid.NewGuid() };

        // Starting at the last index, cycle 2 of 4 should wrap back to index 0.
        var forecast = _service.Forecast(members, startIndex: 2, cycles: 4);

        Assert.Equal(members[2], forecast[0].AssigneeUserId);
        Assert.Equal(members[0], forecast[1].AssigneeUserId);
        Assert.Equal(members[1], forecast[2].AssigneeUserId);
        Assert.Equal(members[2], forecast[3].AssigneeUserId);
    }

    [Fact]
    public void Forecast_EmptyRotation_ReturnsEmptyList()
    {
        var forecast = _service.Forecast(new List<Guid>(), startIndex: 0);

        Assert.Empty(forecast);
    }

    [Fact]
    public void AdvanceRotation_MovesToNextIndex()
    {
        var members = new List<Guid> { Guid.NewGuid(), Guid.NewGuid(), Guid.NewGuid() };

        var next = _service.AdvanceRotation(members, currentIndex: 0);

        Assert.Equal(1, next);
    }

    [Fact]
    public void AdvanceRotation_WrapsAroundAtEnd()
    {
        var members = new List<Guid> { Guid.NewGuid(), Guid.NewGuid(), Guid.NewGuid() };

        var next = _service.AdvanceRotation(members, currentIndex: 2);

        Assert.Equal(0, next);
    }
}
