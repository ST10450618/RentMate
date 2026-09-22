using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using RentMate.Api.Data;

namespace RentMate.Api.Tests;

/// <summary>
/// Boots the real RentMate.Api pipeline (routing, controllers, DTO
/// validation) against an isolated in-memory database and the fake
/// TestAuthHandler, so integration tests exercise genuine HTTP requests
/// instead of calling controller methods directly.
/// </summary>
public class RentMateApiFactory : WebApplicationFactory<Program>
{
    public string DatabaseName { get; } = $"RentMateTests-{Guid.NewGuid()}";

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.ConfigureServices(services =>
        {
            var dbContextDescriptor = services.SingleOrDefault(
                d => d.ServiceType == typeof(DbContextOptions<RentMateDbContext>));
            if (dbContextDescriptor != null) services.Remove(dbContextDescriptor);

            services.AddDbContext<RentMateDbContext>(options =>
            {
                options.UseInMemoryDatabase(DatabaseName);
            });

            services.AddAuthentication(options =>
            {
                options.DefaultAuthenticateScheme = TestAuthHandler.SchemeName;
                options.DefaultChallengeScheme = TestAuthHandler.SchemeName;
            })
            .AddScheme<AuthenticationSchemeOptions, TestAuthHandler>(
                TestAuthHandler.SchemeName, _ => { });
        });
    }
}
