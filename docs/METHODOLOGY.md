# Joulie, CO₂ Tracker Methodology

This document explains how the **Settings → CO₂** card and the **Charts → CO₂ tab** compute their numbers, where the default coefficients come from, and what caveats matter when interpreting them. It is the canonical reference for the constants in [`CO2Calculator`](../app/src/main/java/org/spsl/evtracker/domain/service/CO2Calculator.kt).

---

## Formulas

CO₂ tracking is **opt-in** (`co2Enabled` in DataStore, default `false`). When enabled with an Electricity Maps API key + zone, each charge event captures the grid carbon intensity at save time into `event.gridIntensityGCo2PerKwh`. Events saved while CO₂ was disabled (or while the API key was missing / unreachable) carry `gridIntensityGCo2PerKwh = null` and do **not** contribute to the formulas below; there is no static-grid-intensity fallback (TASK-81).

For each charge event in the visible period:

- **EV-side emissions** (kg CO₂)
  `event.kwhAdded × event.gridIntensityGCo2PerKwh / 1000` (when `gridIntensityGCo2PerKwh != null`)

For the period as a whole:

- **Period EV emissions** (kg CO₂)
  `Σ (event.kwhAdded × event.gridIntensityGCo2PerKwh / 1000)` over events with a live intensity captured.
  Returns `null` when no event in the period contributed. The Dashboard CO₂ card hides on null.

- **Period ICE counterfactual** (kg CO₂)
  `(periodTotalDistanceKm / 100) × iceBaselineLPer100km × 2.31`
  Suppressed by the consumer when EV emissions are `null` so the comparison stays symmetric.

- **Period saved** (kg CO₂, may be negative)
  `iceCounterfactual − evEmissions`

The Charts CO₂ tab renders the cumulative running totals over the period's events:

- **Cumulative EV emissions** advances on every event by `event.kwhAdded × event.gridIntensityGCo2PerKwh / 1000` when the per-event intensity is non-null; otherwise the running total holds.
- **Cumulative ICE counterfactual** advances only on positive odometer deltas (mirrors the StatsCalculator pairwise convention from `DESIGN.md §7`); the first event in the period contributes 0 because there is no prior odometer to delta against.
- The whole series is empty when no event in the period carries a live intensity. The CO₂ tab renders an empty-state "enable Electricity Maps to track CO₂" message rather than a misleading 0-vs-ICE chart.

---

## Coefficients

### Petrol (gasoline) CO₂ emission factor, **2.31 kg CO₂ per litre**

Source: US Environmental Protection Agency, *Greenhouse Gas Emissions from a Typical Passenger Vehicle*. The EPA quotes 8.887 kg CO₂ per gallon of motor gasoline; converted to litres at 3.78541 L/gal that's 2.348 kg/L. The 2.31 figure used here matches the EU reporting convention (which assumes a slightly lower carbon density per litre because of lower ethanol content and tighter regulation on aromatics).

This is a **tank-to-wheel** factor, it counts only the CO₂ released when the petrol burns in the engine, not the upstream refining and transport emissions (which would add ~17%). The EV side uses tank-equivalent (grid intensity at point-of-charge), keeping the comparison apples-to-apples.

### Petrol baseline, **7.0 L/100 km (default)**

Source: European Environment Agency, *Real-world fuel consumption of new passenger cars in the EU*. The 7.0 L/100km figure represents the EU real-world fleet average, what an existing petrol car a household might already own actually consumes in mixed driving. New-car NEDC/WLTP averages are lower (around 5.0 L/100km) but those are aspirational; the 7.0 figure is the more honest counterfactual for "the car the user might have driven instead."

The user can edit this value in **Settings → CO₂ tracker → Petrol baseline** to match their specific vehicle.

### Grid carbon intensity (live, per-event)

Source: the [Electricity Maps API](https://www.electricitymaps.com/free-tier) `v3/carbon-intensity/latest?zone=…` endpoint. The user supplies a personal API token (free tier) and a zone code (default `CY`) in **Settings → CO₂ tracker → Enable CO₂ tracking**. `SaveChargeEventUseCase` fetches the current intensity at save time and stores it on the charge-event row; `ObserveDashboardStatsUseCase` + `CO2Calculator` use the per-event values directly.

The repository (`ElectricityMapsRepository`) enforces a hard 1-hour throttle: the API is called **at most once per zone per hour**, even across process restarts. The cache is two-layer:

- **In-memory** (fast path), scoped to the current process; cleared on `clearCache()`.
- **Persistent** (DataStore): `electricityMapsCacheZone` + `electricityMapsCacheIntensity` + `electricityMapsCacheFetchedAtMs` written atomically after every successful fetch. Survives kill / restart.

A `Mutex` serialises concurrent fetches so two simultaneous saves can't both miss the cache.

Real Cyprus grid intensity varies meaningfully by hour of day:

- **Morning solar peak (10am–4pm)** can drop below 350 gCO₂/kWh on clear summer days.
- **Evening peak (7pm–11pm)** can exceed 700 gCO₂/kWh when oil-fired plants ramp to cover residential demand without solar.

The Dashboard "Carbon intensity" pill (TASK-82) surfaces the live value at the top of the screen, colour-coded across five bands so the user can tell at a glance whether now is a good time to charge.

**No manual fallback.** When CO₂ is enabled but the API key is blank, the fetch returns null, or the network is unreachable, the per-event column is left `null` and the dashboard / charts CO₂ surfaces stay hidden. There used to be a static-grid-intensity preference (`gridIntensityGCo2PerKwh = 577.0`); it was removed in TASK-81 because guessing CO₂ from a user-typed number was misleading.

---

## Caveats

### Tank-to-wheel vs well-to-wheel

Both sides of the comparison are tank-to-wheel. **Well-to-wheel** numbers (which include refinery/transport emissions for petrol and grid-loss/upstream emissions for electricity) would shift both sides up by roughly the same percentage and leave the ratio similar. We prefer tank-to-wheel because it's what the user can directly reduce by choosing when to charge.

### Average vs marginal grid intensity

The intensity Electricity Maps returns is an **average** value, what a kWh "looks like" on the user's zone as a whole at that moment. But every additional kWh the user draws is met by the **marginal** plant on the margin, which is almost always the most expensive (typically oil-fired) generator in the merit order. Marginal intensity is usually *higher* than average. Researchers comparing EV impact should treat the per-event values stored by Joulie as a lower bound for genuinely-induced emissions. A marginal-factor refinement would need either a paid Electricity Maps tier or an ENTSO-E mix-derivation model, neither in scope today.

### Period scoping

The Dashboard CO₂ card honours the same period chips as the rest of the Dashboard (7 days / 30 days / year / custom). The Charts CO₂ tab uses the Charts period chips. Both numbers in the card recompute when the user changes period, they are not lifetime totals.

### Saved can be negative

On a high-grid-intensity period with low driven distance, EV emissions can exceed the petrol counterfactual. The card surfaces this honestly with a "*X.X kg more than petrol*" label rather than hiding the number, that's a real signal that the user's recent charging happened during dirty grid hours.

---

## Open issues

### Per-event grid intensity ✅ shipped

Resolved by TASK-80 (Electricity Maps integration) + TASK-81 (drop static fallback + persistent throttle) + TASK-82 (dashboard pill + boot refresh). The user supplies their own Electricity Maps API key on the free tier; Joulie respects the once-per-zone-per-hour rate limit and never falls back to a typed-by-the-user grid-intensity number.

Earlier candidates (kept here for historical context):

- **Electricity Maps API**, adopted. Free tier is once-per-hour-per-zone; matches our throttle exactly.
- **CO2Signal**, absorbed into Electricity Maps' product.
- **cyprusgrid.com direct**, bot-blocked behind a WAF; not stable enough.
- **ENTSO-E Transparency Platform**, covers Cyprus but returns hourly generation mix per production type, not pre-computed carbon intensity. Possible future "without an Electricity Maps key" fallback if/when we want zero-config tracking; not adopted today.
- **TSOC direct API access**, needs email correspondence; not pursued.

### ICE-baseline personalisation

The 7.0 L/100km default is a fleet average. A user who drives a 2024 Toyota Prius (4.5 L/100km) would over-estimate their savings; a user with a 2008 SUV (10 L/100km) would under-estimate. Both can edit the value in Settings; we don't currently offer a make/model lookup. A future iteration could ship a curated table of common EU-market vehicles with their published WLTP figures.

### Driving-style adjustment

The ICE counterfactual assumes the user drives the petrol car the same way they drive the EV. Real-world EV drivers tend to drive more efficiently (smoother acceleration, regenerative braking habit) so the counterfactual is conservative. We don't currently model this; it would require self-reported driving-style data.

---

## Citations

1. US EPA. *Greenhouse Gas Emissions from a Typical Passenger Vehicle.* https://www.epa.gov/greenvehicles/greenhouse-gas-emissions-typical-passenger-vehicle
2. European Environment Agency. *Real-world fuel consumption of new passenger cars in the EU.* https://www.eea.europa.eu/
3. cyprusgrid.com. *Cyprus electricity grid carbon intensity (live).* https://cyprusgrid.com/realtime, accessed 2026-05-04, 365 gCO₂eq/kWh "last hour"; 577 gCO₂/kWh used as the 2025 annual average.
4. ENTSO-E. *Transparency Platform.* https://transparency.entsoe.eu/
