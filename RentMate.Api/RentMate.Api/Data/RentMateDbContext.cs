using Microsoft.EntityFrameworkCore;
using RentMate.Api.Models;

namespace RentMate.Api.Data;

public class RentMateDbContext : DbContext
{
    public RentMateDbContext(DbContextOptions<RentMateDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();
    public DbSet<Household> Households => Set<Household>();
    public DbSet<HouseholdMember> HouseholdMembers => Set<HouseholdMember>();
    public DbSet<Bill> Bills => Set<Bill>();
    public DbSet<BillShare> BillShares => Set<BillShare>();
    public DbSet<Chore> Chores => Set<Chore>();
    public DbSet<ChoreCompletion> ChoreCompletions => Set<ChoreCompletion>();
    public DbSet<ShoppingItem> ShoppingItems => Set<ShoppingItem>();
    public DbSet<MaintenanceRequest> MaintenanceRequests => Set<MaintenanceRequest>();
    public DbSet<Settlement> Settlements => Set<Settlement>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<User>()
            .HasIndex(u => u.Email)
            .IsUnique();

        modelBuilder.Entity<User>()
            .HasIndex(u => u.GoogleSubjectId)
            .IsUnique();

        modelBuilder.Entity<Household>()
            .HasIndex(h => h.InviteCode)
            .IsUnique();

        modelBuilder.Entity<HouseholdMember>()
            .HasIndex(hm => new { hm.HouseholdId, hm.UserId })
            .IsUnique();

        modelBuilder.Entity<HouseholdMember>()
            .HasOne(hm => hm.Household)
            .WithMany(h => h.Members)
            .HasForeignKey(hm => hm.HouseholdId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<HouseholdMember>()
            .HasOne(hm => hm.User)
            .WithMany(u => u.HouseholdMemberships)
            .HasForeignKey(hm => hm.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<Bill>()
            .HasOne(b => b.Household)
            .WithMany(h => h.Bills)
            .HasForeignKey(b => b.HouseholdId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<Bill>()
            .HasOne(b => b.IssuedByUser)
            .WithMany()
            .HasForeignKey(b => b.IssuedByUserId)
            .OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<BillShare>()
            .HasOne(bs => bs.Bill)
            .WithMany(b => b.Shares)
            .HasForeignKey(bs => bs.BillId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<BillShare>()
            .HasOne(bs => bs.User)
            .WithMany()
            .HasForeignKey(bs => bs.UserId)
            .OnDelete(DeleteBehavior.Restrict);

        // Store decimal money fields with explicit precision so SQL Server
        // does not silently truncate cents.
        modelBuilder.Entity<Bill>().Property(b => b.Amount).HasPrecision(18, 2);
        modelBuilder.Entity<BillShare>().Property(bs => bs.AmountOwed).HasPrecision(18, 2);
        modelBuilder.Entity<Settlement>().Property(s => s.Amount).HasPrecision(18, 2);

        modelBuilder.Entity<Chore>()
            .HasOne(c => c.Household)
            .WithMany(h => h.Chores)
            .HasForeignKey(c => c.HouseholdId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<ChoreCompletion>()
            .HasOne(cc => cc.Chore)
            .WithMany(c => c.Completions)
            .HasForeignKey(cc => cc.ChoreId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<ChoreCompletion>()
            .HasOne(cc => cc.CompletedByUser)
            .WithMany()
            .HasForeignKey(cc => cc.CompletedByUserId)
            .OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<ShoppingItem>()
            .HasOne(si => si.Household)
            .WithMany(h => h.ShoppingItems)
            .HasForeignKey(si => si.HouseholdId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<ShoppingItem>()
            .HasOne(si => si.AddedByUser)
            .WithMany()
            .HasForeignKey(si => si.AddedByUserId)
            .OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<MaintenanceRequest>()
            .HasOne(m => m.Household)
            .WithMany(h => h.MaintenanceRequests)
            .HasForeignKey(m => m.HouseholdId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<MaintenanceRequest>()
            .HasOne(m => m.RaisedByUser)
            .WithMany()
            .HasForeignKey(m => m.RaisedByUserId)
            .OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<Settlement>()
            .HasOne(s => s.Household)
            .WithMany(h => h.Settlements)
            .HasForeignKey(s => s.HouseholdId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<Settlement>()
            .HasOne(s => s.FromUser)
            .WithMany()
            .HasForeignKey(s => s.FromUserId)
            .OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<Settlement>()
            .HasOne(s => s.ToUser)
            .WithMany()
            .HasForeignKey(s => s.ToUserId)
            .OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<RefreshToken>()
            .HasOne(rt => rt.User)
            .WithMany(u => u.RefreshTokens)
            .HasForeignKey(rt => rt.UserId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}
