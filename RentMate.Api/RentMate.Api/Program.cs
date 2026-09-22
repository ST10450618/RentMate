using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using RentMate.Api.Data;
using RentMate.Api.Services;

var builder = WebApplication.CreateBuilder(args);

// --- Database -------------------------------------------------------------
// Local dev uses the InMemory provider by default so the whole team can run
// the API with zero setup; set ConnectionStrings:RentMate (see README) to
// point at Azure SQL / a local SQL Server instance for anything that needs
// to persist data between runs.
var connectionString = builder.Configuration.GetConnectionString("RentMate");

builder.Services.AddDbContext<RentMateDbContext>(options =>
{
    if (string.IsNullOrWhiteSpace(connectionString))
    {
        options.UseInMemoryDatabase("RentMateDev");
    }
    else
    {
        options.UseSqlServer(connectionString);
    }
});

// --- Auth -------------------------------------------------------------------
var jwtKey = builder.Configuration["Jwt:Key"] ?? "DEV-ONLY-CHANGE-ME-this-key-is-not-secure-32chars+";
var jwtIssuer = builder.Configuration["Jwt:Issuer"] ?? "RentMate.Api";
var jwtAudience = builder.Configuration["Jwt:Audience"] ?? "RentMate.App";

builder.Services.AddAuthentication(options =>
{
    options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
    options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
})
.AddJwtBearer(options =>
{
    options.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuer = true,
        ValidIssuer = jwtIssuer,
        ValidateAudience = true,
        ValidAudience = jwtAudience,
        ValidateIssuerSigningKey = true,
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey)),
        ValidateLifetime = true,
        ClockSkew = TimeSpan.FromSeconds(30)
    };
});

builder.Services.AddAuthorization();

// --- App services -----------------------------------------------------------
builder.Services.AddScoped<IJwtTokenService, JwtTokenService>();
builder.Services.AddScoped<IGoogleAuthValidator, GoogleAuthValidator>();
builder.Services.AddScoped<IDebtSimplificationService, DebtSimplificationService>();
builder.Services.AddScoped<IChoreRotationService, ChoreRotationService>();
builder.Services.AddScoped<IInviteCodeGenerator, InviteCodeGenerator>();
builder.Services.AddSingleton<INotificationService, FcmNotificationService>();

builder.Services.AddControllers().AddJsonOptions(options =>
{
    // Serialise enums (SplitMethod, MaintenanceStatus, ChoreRecurrence, ...)
    // as their string names ("Equal", "Plumbing", "Weekly") rather than raw
    // integers, both in requests and responses. This matches every example
    // in RentMate.postman_collection.json and is far easier for whoever
    // writes the Android networking/DTO layer to work with than guessing
    // what enum value 2 means.
    options.JsonSerializerOptions.Converters.Add(new System.Text.Json.Serialization.JsonStringEnumConverter());
});

// --- Swagger / OpenAPI, with a JWT bearer field for easy manual testing ----
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo { Title = "RentMate API", Version = "v1" });

    options.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Description = "Paste just the JWT access token - no need to type 'Bearer' in front of it.",
        Name = "Authorization",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT"
    });

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" }
            },
            Array.Empty<string>()
        }
    });
});

// --- CORS: the Android client does not need this, but it keeps Swagger UI
// and any teammate testing from a browser painless. -------------------------
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod();
    });
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}
else
{
    // Swagger stays on in production too, deliberately - the lecturer and
    // teammates need a quick way to see and try every endpoint on the
    // deployed Azure instance without a Postman collection in hand.
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseCors();
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.MapGet("/", () => Results.Redirect("/swagger"));

app.Logger.LogInformation("RentMate API starting up. Environment: {Environment}", app.Environment.EnvironmentName);

app.Run();

// Exposed so the test project's WebApplicationFactory<Program> can find this
// entry point.
public partial class Program { }
