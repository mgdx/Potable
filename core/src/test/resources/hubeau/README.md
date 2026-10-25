# Hub'Eau fixtures

Responses of the Hub'Eau `qualite_eau_potable` API (v1), captured with
`curl --max-time 180` and stored byte for byte, except where noted. Base URL:
`https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/`.

| File | Request | Captured (UTC) | Content |
|---|---|---|---|
| `communes_udi_45234.json` | `communes_udi?code_commune=45234&size=100` | 2026-10-25 00:03 | Orléans: one UDI (`045000474`), 11 years (2016–2026), `nom_reseau` changing over the years (`ORLEANS` → `ORLEANS-ST JEAN LE BLANC-ST PRYVÉ` → `…-ST DENIS EN VAL` in 2026), `nom_quartier` = `totalité` |
| `communes_udi_75056.json` | `communes_udi?code_commune=75056&size=100` | 2026-10-25 00:03 | Paris: four UDI per year, each with a `nom_quartier`, 2016–2026 (44 rows) |
| `communes_udi_97501.json` | `communes_udi?code_commune=97501&size=100` | 2026-10-25 00:03 | Miquelon-Langlade (Saint-Pierre-et-Miquelon): `count: 0`, no data (97502 Saint-Pierre gives the same) |
| `resultats_dis_45234.json` | `resultats_dis?code_commune=45234&sort=desc&size=100` | 2026-10-25 00:04 | Orléans, first page of 100 rows: 3 complete samples (27, 27, 28 rows) and the first 18 rows of a fourth. Non-numeric results: `N.M.` (`resultat_numerique` null), `<0,01`, `<1` (`resultat_numerique` 0.0) |
| `resultats_dis_31555_sans_code_parametre.json` | `resultats_dis?code_commune=31555&sort=desc&size=500`, **reduced** | 2026-10-25 01:00 | Toulouse, sample `03100216050` (2026-08-31, 29 rows): envelope as captured, `data` cut down to 5 of its rows in API order (positions 7, 17–20), one of which is "Prélèvement sous acréditation" with `code_parametre` **null** (`code_parametre_se` `PLVACR`, `resultat_alphanumerique` `OUI`). Every sample of the first page carries such a row |
| `resultats_dis_75056_ph.json` | `resultats_dis?code_commune=75056&code_reseau=075000221&code_parametre=1302&date_min_prelevement=2026-06-29&date_max_prelevement=2026-07-01&fields=code_prelevement,code_commune,date_prelevement,code_parametre,libelle_parametre,code_lieu_analyse,resultat_alphanumerique,resultat_numerique,libelle_unite,limite_qualite_parametre,reference_qualite_parametre&sort=desc&size=20000` | 2026-10-25 01:37 | Paris CENTRE, pH of 5 samples in 8 rows: 3 samples publish it twice, on site (`code_lieu_analyse` `T`) and in the laboratory (`L`), with different values; reference `>=6,5 et <=9 unité pH`, no limit |
| `resultats_dis_75056_anthraquinone.json` | `resultats_dis?code_commune=75056&code_reseau=075000227&code_parametre=2013&date_min_prelevement=2025-10-25&date_max_prelevement=2026-10-26&fields=code_prelevement,code_commune,date_prelevement,code_parametre,libelle_parametre,code_lieu_analyse,resultat_alphanumerique,resultat_numerique,libelle_unite,limite_qualite_parametre,reference_qualite_parametre&sort=desc&page=1&size=5000` | 2026-10-25 | Paris, anthraquinone (`2013`), 12 rows of 7 samples: 5 samples publish it twice under the same code, in the laboratory both times, as `Anthraquinone (HAP)` without limit and `Anthraquinone (pesticide)` with limit `<=0,1 µg/L`, in either order |
| `resultats_dis_45234_frise.json` | `resultats_dis?code_commune=45234&code_reseau=045000474&code_parametre=1302,1382,1449&date_min_prelevement=2026-08-01&date_max_prelevement=2026-10-26&fields=code_prelevement,code_commune,date_prelevement,conclusion_conformite_prelevement,conformite_limites_bact_prelevement,conformite_limites_pc_prelevement,conformite_references_bact_prelevement,conformite_references_pc_prelevement&sort=desc&size=20000` | 2026-10-25 01:37 | Orléans, timeline request (`docs/hubeau-api.md`): 8 header rows of 4 samples |
| `resultats_dis_45234_ph.json` | `resultats_dis?code_commune=45234&code_reseau=045000474&code_parametre=1302&date_min_prelevement=2026-07-01&date_max_prelevement=2026-10-27&fields=code_prelevement,code_commune,date_prelevement,code_parametre,libelle_parametre,code_lieu_analyse,resultat_alphanumerique,resultat_numerique,libelle_unite,limite_qualite_parametre,reference_qualite_parametre&sort=desc&page=1&size=5000` | 2026-10-25 01:58 | Orléans, pH, 10 samples (7,5 to 7,7): reference `>=6,5 et <=9 unité pH`, no limit. Copied to `data/src/test/resources/hubeau/` |
| `resultats_dis_45234_nitrates.json` | same, `code_parametre=1340` | 2026-10-25 01:59 | Orléans, nitrates, 10 samples (2,5 to 6,3 mg/L): limit `<=50 mg/L`, no reference. Copied to `data/src/test/resources/hubeau/` |
| `resultats_dis_sans_conclusion.json` | **fabricated** from `resultats_dis_45234.json` | 2026-10-25 | Three rows of sample `04500171315` with `conclusion_conformite_prelevement` removed, `conformite_limites_bact_prelevement` = `""`, `conformite_limites_pc_prelevement` = `null`, both `conformite_references_*` removed |

A sample without a conclusion could not be found in the real data: scans of
5 000 rows each for 45234, 75056, 13055, 2A004, 97302 and 97611 (2026-10-25)
all carried a conclusion, hence the fabricated variant.

The compliance codes (`conformite_*_prelevement`) are documented in the API's
swagger, `https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/api-docs`:
empty, `C`, `N`, `D`, `S`.
