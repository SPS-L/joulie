# Joulie — CO₂ Tracker Methodology

This document explains how the **Settings → CO₂** card and the **Charts → CO₂ tab** compute their numbers, where the default coefficients come from, and what caveats matter when interpreting them. It is the canonical reference for the constants in [`CO2Calculator`](../app/src/main/java/org/spsl/evtracker/domain/service/CO2Calculator.kt).

The CO₂ tracker shipped in **TASK-20** (2026-05-04, app version 1.7.0).

---

## Formulas

For each charge event in the visible period:

- **EV-side emissions** (kg CO₂)
  `kwhAdded × gridIntensityGCo2PerKwh / 1000`

For the period as a whole:

- **Period EV emissions** (kg CO₂)
  `Σ kwhAdded over period events × gridIntensityGCo2PerKwh / 1000`

- **Period ICE counterfactual** (kg CO₂)
  `(periodTotalDistanceKm / 100) × iceBaselineLPer100km × 2.31`

- **Period saved** (kg CO₂, may be negative)
  `iceCounterfactual − evEmissions`

The Charts CO₂ tab renders the cumulative running totals over the period's events:

- **Cumulative EV emissions** advances on every event by `kwhAdded × gridIntensityGCo2PerKwh / 1000`.
- **Cumulative ICE counterfactual** advances only on positive odometer deltas (mirrors the StatsCalculator pairwise convention from `DESIGN.md §7`); the first event in the period contributes 0 because there is no prior odometer to delta against.

---

## Coefficients

### Petrol (gasoline) CO₂ emission factor — **2.31 kg CO₂ per litre**

Source: US Environmental Protection Agency, *Greenhouse Gas Emissions from a Typical Passenger Vehicle*. The EPA quotes 8.887 kg CO₂ per gallon of motor gasoline; converted to litres at 3.78541 L/gal that's 2.348 kg/L. The 2.31 figure used here matches the EU reporting convention (which assumes a slightly lower carbon density per litre because of lower ethanol content and tighter regulation on aromatics).

This is a **tank-to-wheel** factor — it counts only the CO₂ released when the petrol burns in the engine, not the upstream refining and transport emissions (which would add ~17%). The EV side uses tank-equivalent (grid intensity at point-of-charge), keeping the comparison apples-to-apples.

### Petrol baseline — **7.0 L/100 km (default)**

Source: European Environment Agency, *Real-world fuel consumption of new passenger cars in the EU*. The 7.0 L/100km figure represents the EU real-world fleet average — what an existing petrol car a household might already own actually consumes in mixed driving. New-car NEDC/WLTP averages are lower (around 5.0 L/100km) but those are aspirational; the 7.0 figure is the more honest counterfactual for "the car the user might have driven instead."

The user can edit this value in **Settings → CO₂ tracker → Petrol baseline** to match their specific vehicle.

### Grid carbon intensity — **577 gCO₂/kWh (default)**

Source: [cyprusgrid.com](https://cyprusgrid.com/realtime), the publicly-published live Cyprus grid-intensity tracker. The 577 figure is the 2025 annual average — Cyprus's electricity mix is still dominated by oil-fired generation (heavy fuel oil and diesel) but PV penetration has grown rapidly, so historical TSOC/IEA figures (around 600 gCO₂/kWh) understate the renewable share by about 4%.

Real Cyprus grid intensity varies meaningfully by hour of day:

- **Morning solar peak (10am–4pm)** can drop below 350 gCO₂/kWh on clear summer days.
- **Evening peak (7pm–11pm)** can exceed 700 gCO₂/kWh when oil-fired plants ramp to cover residential demand without solar.

TASK-20 uses a static yearly average; the per-event variation will land in **TASK-49** (deferred — see *Open issues* below).

The user can edit this value in **Settings → CO₂ tracker → Grid intensity** to reflect their charging habits or a different region.

---

## Caveats

### Tank-to-wheel vs well-to-wheel

Both sides of the comparison are tank-to-wheel. **Well-to-wheel** numbers (which include refinery/transport emissions for petrol and grid-loss/upstream emissions for electricity) would shift both sides up by roughly the same percentage and leave the ratio similar. We prefer tank-to-wheel because it's what the user can directly reduce by choosing when to charge.

### Average vs marginal grid intensity

The 577 gCO₂/kWh default is an **average** intensity — what a kWh "looks like" on the Cyprus grid as a whole. But every additional kWh the user draws is met by the **marginal** plant on the margin at that moment, which is almost always the most expensive (typically oil-fired) generator in the merit order. Marginal intensity in Cyprus is often *higher* than average. Researchers comparing EV impact should treat the 577 figure as a lower bound for genuinely-induced emissions.

### Period scoping

The Dashboard CO₂ card honours the same period chips as the rest of the Dashboard (7 days / 30 days / year / custom). The Charts CO₂ tab uses the Charts period chips. Both numbers in the card recompute when the user changes period — they are not lifetime totals.

### Saved can be negative

On a high-grid-intensity period with low driven distance, EV emissions can exceed the petrol counterfactual. The card surfaces this honestly with a "*X.X kg more than petrol*" label rather than hiding the number — that's a real signal that the user's recent charging happened during dirty grid hours.

---

## Open issues

### Per-event grid intensity (TASK-49)

The deferred TASK-49 will replace the static `gridIntensityGCo2PerKwh` preference with a per-event live value fetched at save time. The intent is to surface "charge during the solar peak for cleaner CO₂" as a behavioural nudge. Required: a free real-time Cyprus grid-mix data source. Candidates evaluated and not yet adopted:

- **Electricity Maps API** — closest fit functionally, but priced at €6,000/year per zone for Carbon Intensity. Out of budget.
- **CO2Signal** — was free; absorbed into Electricity Maps' paid product.
- **cyprusgrid.com direct** — bot-blocked behind a WAF; not stable enough as a production data source.
- **ENTSO-E Transparency Platform** — free, registered API, Cyprus covered, but returns hourly generation mix per production type (not pre-computed carbon intensity). Would require deriving intensity from per-source CO₂ emission factors. Real work; deferred.
- **TSOC direct API access** — best long-term answer; needs email correspondence.

When TASK-49 lands, this document will be extended with the per-event source's methodology and emission factors.

### ICE-baseline personalisation

The 7.0 L/100km default is a fleet average. A user who drives a 2024 Toyota Prius (4.5 L/100km) would over-estimate their savings; a user with a 2008 SUV (10 L/100km) would under-estimate. Both can edit the value in Settings; we don't currently offer a make/model lookup. A future iteration could ship a curated table of common EU-market vehicles with their published WLTP figures.

### Driving-style adjustment

The ICE counterfactual assumes the user drives the petrol car the same way they drive the EV. Real-world EV drivers tend to drive more efficiently (smoother acceleration, regenerative braking habit) so the counterfactual is conservative. We don't currently model this; it would require self-reported driving-style data.

---

## Citations

1. US EPA. *Greenhouse Gas Emissions from a Typical Passenger Vehicle.* https://www.epa.gov/greenvehicles/greenhouse-gas-emissions-typical-passenger-vehicle
2. European Environment Agency. *Real-world fuel consumption of new passenger cars in the EU.* https://www.eea.europa.eu/
3. cyprusgrid.com. *Cyprus electricity grid carbon intensity (live).* https://cyprusgrid.com/realtime — accessed 2026-05-04, 365 gCO₂eq/kWh "last hour"; 577 gCO₂/kWh used as the 2025 annual average.
4. ENTSO-E. *Transparency Platform.* https://transparency.entsoe.eu/
