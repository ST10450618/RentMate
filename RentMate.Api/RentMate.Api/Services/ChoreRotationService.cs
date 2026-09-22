using RentMate.Api.Models;

namespace RentMate.Api.Services;

public record ForecastEntry(int CycleNumber, Guid AssigneeUserId);

public interface IChoreRotationService
{
    /// <summary>
    /// Projects the next N cycles of a chore's rotation from a given
    /// starting index, without mutating anything (US-9: "next 4 cycles
    /// rotation is dealt with server-side during creation").
    /// </summary>
    List<ForecastEntry> Forecast(List<Guid> rotationOrder, int startIndex, int cycles = 4);

    /// <summary>Advances the rotation by one position and returns the new index.</summary>
    int AdvanceRotation(List<Guid> rotationOrder, int currentIndex);
}

public class ChoreRotationService : IChoreRotationService
{
    public List<ForecastEntry> Forecast(List<Guid> rotationOrder, int startIndex, int cycles = 4)
    {
        var forecast = new List<ForecastEntry>();
        if (rotationOrder.Count == 0) return forecast;

        for (var i = 0; i < cycles; i++)
        {
            var index = (startIndex + i) % rotationOrder.Count;
            forecast.Add(new ForecastEntry(i + 1, rotationOrder[index]));
        }

        return forecast;
    }

    public int AdvanceRotation(List<Guid> rotationOrder, int currentIndex)
    {
        if (rotationOrder.Count == 0) return 0;
        return (currentIndex + 1) % rotationOrder.Count;
    }
}
