package name.abuchen.portfolio.ui.views;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.Test;

import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.util.Pair;

public class SecuritiesTableQuoteChangeTest
{
    private static final ReportingPeriod LAST_DAY = new ReportingPeriod.LastXDays(1);

    /**
     * Daily quotes from Monday 2026-11-02 to Friday 2026-11-20 (weekdays only),
     * starting at 101 and increasing by 1 per trading day.
     */
    private static Security dailyQuotes()
    {
        var security = new Security("Daily", "EUR");
        var value = 100;
        for (var date = LocalDate.parse("2026-11-02"); !date.isAfter(LocalDate.parse("2026-11-20")); date = date
                        .plusDays(1))
        {
            if (date.getDayOfWeek().getValue() <= 5)
                security.addPrice(quote(date.toString(), ++value));
        }
        return security;
    }

    private static SecurityPrice quote(String date, double value)
    {
        return new SecurityPrice(LocalDate.parse(date), Values.Quote.factorize(value));
    }

    private static void assertPrices(Optional<Pair<SecurityPrice, SecurityPrice>> prices, String previous,
                    String latest)
    {
        assertThat(prices.isPresent(), is(true));
        assertThat(prices.get().getLeft().getDate(), is(LocalDate.parse(previous)));
        assertThat(prices.get().getRight().getDate(), is(LocalDate.parse(latest)));
    }

    @Test
    public void testDailyQuotesUseQuotesOfReportingPeriod()
    {
        var prices = SecuritiesTable.getPricesOfReportingPeriod(dailyQuotes(), LAST_DAY,
                        LocalDate.parse("2026-11-17"));

        assertPrices(prices, "2026-11-16", "2026-11-17");
    }

    @Test
    public void testDelayedNavComparesLastTwoPublishedQuotes()
    {
        var security = new Security("Weekly", "EUR");
        security.addPrice(quote("2026-11-13", 200));
        security.addPrice(quote("2026-11-20", 207));

        var prices = SecuritiesTable.getPricesOfReportingPeriod(security, LAST_DAY, LocalDate.parse("2026-11-25"));

        assertPrices(prices, "2026-11-13", "2026-11-20");
    }

    @Test
    public void testWeekendComparesLastTradingDayWithDayBefore()
    {
        var security = dailyQuotes();

        assertPrices(SecuritiesTable.getPricesOfReportingPeriod(security, LAST_DAY, LocalDate.parse("2026-11-21")),
                        "2026-11-19", "2026-11-20");
        assertPrices(SecuritiesTable.getPricesOfReportingPeriod(security, LAST_DAY, LocalDate.parse("2026-11-22")),
                        "2026-11-19", "2026-11-20");
    }

    @Test
    public void testFallbackKeepsQuoteAtEndOfReportingPeriod()
    {
        // weekend in the past: both ends resolve to Friday 2026-11-06, which
        // must be compared with Thursday 2026-11-05 and not with the latest
        // quotes of the security
        var period = new ReportingPeriod.FromXtoY(LocalDate.parse("2026-11-07"), LocalDate.parse("2026-11-08"));

        var prices = SecuritiesTable.getPricesOfReportingPeriod(dailyQuotes(), period, LocalDate.parse("2026-11-24"));

        assertPrices(prices, "2026-11-05", "2026-11-06");
    }

    @Test
    public void testFallbackSkipsZeroQuotes()
    {
        var security = new Security("Zero", "EUR");
        security.addPrice(quote("2026-11-18", 100));
        security.addPrice(new SecurityPrice(LocalDate.parse("2026-11-19"), 0));
        security.addPrice(quote("2026-11-20", 105));

        var prices = SecuritiesTable.getPricesOfReportingPeriod(security, LAST_DAY, LocalDate.parse("2026-11-24"));

        assertPrices(prices, "2026-11-18", "2026-11-20");
    }

    @Test
    public void testSingleQuoteHasNoChange()
    {
        var security = new Security("Single", "EUR");
        security.addPrice(quote("2026-11-20", 10));

        var prices = SecuritiesTable.getPricesOfReportingPeriod(security, LAST_DAY, LocalDate.parse("2026-11-24"));

        assertThat(prices.isPresent(), is(false));
    }

    @Test
    public void testNoQuoteBeforeStartOfReportingPeriod()
    {
        var security = new Security("New", "EUR");
        security.addPrice(quote("2026-11-24", 10));

        var prices = SecuritiesTable.getPricesOfReportingPeriod(security, LAST_DAY, LocalDate.parse("2026-11-24"));

        assertThat(prices.isPresent(), is(false));
    }
}
