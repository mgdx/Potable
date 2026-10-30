# F-Droid review — io.github.mgdx.potable 0.5.0 (8)

**Verdict.** Ready to merge; nothing in the app or in the recipe blocks inclusion.

**Issues.**
None.

**Findings.** No anti-feature is declared. The app depends entirely on the Hub'Eau API (`hubeau.eaufrance.fr`), whose base URL is not configurable and whose server software is not published (github.com/BRGM/hubeau only holds docs and examples), so a reviewer may ask about `NonFreeNet` or `TetheredNet`. The author's position: Hub'Eau is a free public service of the French State, with no account or key, serving open data under Licence Ouverte Etalab 2.0 that is also downloadable from data.gouv.fr, and the app only reads it. No connection happens on start: the first one is made when the user opens a commune.

| Domain | When | Volume | Described |
|---|---|---|---|
| hubeau.eaufrance.fr (HTTPS) | on opening a commune, history, parameter chart | 733 packets, 210 kB | yes, in the Privacy section |

**Not verified.** VirusTotal: the API refused unauthenticated lookups and the APK was not uploaded, so the Security Scan box stays empty. `fdroid lint` / `fdroid build`: fdroidserver not installed and no fdroiddata MR yet. Reproducibility was checked only locally: an unsigned build of a fresh clone at b6693d7 has the same 136 entries, byte for byte, as the published APK; the F-Droid build server itself was not tried. Background alert checks (WorkManager) were not captured, since notifications were denied during the test.

**Remarks and questions for the author** (none of them blocking).
- The ANSES metabolite table is treated as public information rather than anses.fr's CC BY-NC-ND content: is that interpretation documented enough to avoid `NonFreeAssets`?
- `communes.tsv` embeds La Poste postal codes via geo.api.gouv.fr: is their licence confirmed as Etalab 2.0?
- No `featureGraphic` in Fastlane.

**Context.** APK under test: signed release `potable-0.5.0.apk` from the GitHub release v0.5.0 (not an F-Droid build), sha256 `be6f4fa3…ac75fc16`, identical to a local build of commit b6693d7. Device: Fairphone 3, LineageOS, Android 15. Network capture: PCAPdroid VPN mode filtered on the app UID, started before first launch, about 7 minutes, fresh install. CI build: no merge request yet. Date: 2026-10-30.

<table>
  <thead>
    <tr>
      <th>Category</th>
      <th>Checklist</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Basic Function</td>
      <td>

- [x] The app can start and work normally.
- [x] The functions in the description are implemented.
- [x] The app has a unique icon (instead of a default one).

</td>
    </tr>
    <tr>
      <td>Policy Compliance</td>
      <td>

- [x] Features don't violate F-Droid's Inclusion Policy.
- [x] The Categories field is set properly.
- [x] Doesn't require accepting any terms other than the FOSS license.

</td>
    </tr>
    <tr>
      <td>Permissions<br>(Whether the app requires unnecessary permissions. Permissions are listed in Code Quality.)</td>
      <td>

- [x] The app can be used without granting optional runtime permissions. The app should only require necessary runtime permissions for basic functions on start and other runtime permissions should be required when corresponding functions are used. For example, a messaging app should only require CAMERA permission when the user starts a video chat.
- [x] The app doesn't require unnecessary MANAGE_EXTERNAL_STORAGE permission. If it does, suggest using SAF instead when it's possible.

</td>
    </tr>
    <tr>
      <td>Network Connections<br>(If the app requires <code>INTERNET</code> permission then the network connections need to be checked with network monitors, e.g. <a href="https://f-droid.org/en/packages/com.emanuelef.remote_capture">PCAPdroid</a>. Start the monitor before opening the app to make sure all the network connections are catched.)</td>
      <td>

- [x] Network connection is observed. If not, please ask the author to remove the permission.
- [ ] The app connects to web services on start. Such connections means NonFreeNet and TetheredNet may apply.
- [ ] The app checks for update automatically.
- [ ] The app has unnecessary connections, e.g., online fonts, icons, connectivity check, etc. Such connections should be removed or made optional.
- [ ] Tracking domains connected.
- [ ] Connections not described clearly in description. Such connections should be checked with the author.
- [ ] Unnecessary in-app webview (e.g. for author page) presents in the app. Such thing should be replaced with opening the url in browsers.

</td>
    </tr>
    <tr>
      <td>Language Support</td>
      <td>

- [x] The app has English support. If English is not supported this should be declared in the description.

</td>
    </tr>
    <tr>
      <td>Security Scan</td>
      <td>

- [ ] All or most vendors on VirusTotal or similar scanning services indicate the app is benign.

</td>
    </tr>
  </tbody>
</table>

