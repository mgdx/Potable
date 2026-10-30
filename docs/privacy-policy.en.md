# Privacy policy

*Potable — `io.github.mgdx.potable`. Version of 30 October 2026.*

*[Version française](privacy-policy.md). If the two versions differ, the
French one prevails.*

Potable collects nothing about you. There is no account, no advertising, no
analytics, no crash reporting, no unique identifier, and no third-party
software development kit that could carry one. Nothing is sent to the author
of the application, who therefore holds no data on anybody and could hand
none over if asked.

This document says what happens instead, because "we collect nothing" is easy
to write and worth nothing if it cannot be checked. The application is free
software under the GNU GPL v3: its
[source code](https://github.com/mgdx/Potable) is the reference, and every
statement below can be checked against it.

## What stays on your device

Three things are written to the application's private storage, which no other
application can read:

- **Your settings**: whether you acknowledged the first-launch notice, the
  theme, the history window, how long the cache is kept, the alerts and how
  often they run, and the distribution unit chosen for each commune where you
  chose one. "Clear cached data" forgets that choice for every commune you do not
  follow; clearing the cache of a single commune forgets it for that commune
  if you do not follow it. Added to these are the last five distribution units
  whose sheet you opened (codes and names of the commune and of the unit),
  offered by the search screen; each one can be removed from there with one
  gesture.
- **The communes you follow**, with their distribution unit.
- **The cache of public data for the communes you follow and the last five
  you viewed**: their latest sample, the samples opened from the history, the
  series of the parameters you looked at, the timeline of their conclusions,
  and, for followed communes only, the date of the latest sample seen by the
  alerts. This is data published by Hub'Eau, the same for everybody. The data
  of a distribution unit is erased as soon as it is neither followed nor among
  the recently viewed communes (removed from the list, pushed out of it by a
  newer one, or unfollowed). It is also purged beyond the retention period you
  chose (3 years by default); in the settings, "Clear cached data" erases all of it,
  and the list of cached communes lets you erase a single commune's data.

Uninstalling the application erases all of it.

The reference list of communes shipped with the application (names, codes,
centroids) is a public, read-only file, identical on every device.

## What is never stored

- **Your location.**
- What you type in the search field.

## Your location

The approximate location permission (`ACCESS_COARSE_LOCATION`) is only asked
for when you tap "Near me", never at launch. Refusing it blocks nothing:
searching by name or postal code remains available.

If you grant it, the location is read from the Android system's location
service, never from Google services, which the application does not use. It
is used **once**, on the phone, to find the commune whose centre is nearest in
the embedded reference list, and is then discarded. It is neither stored nor
sent: no request made by the application contains it.

Depending on your device, the system's location service may itself rely on a
network location provider. That is a system setting, independent of Potable.

## What goes over the network

The application contacts **a single domain: `hubeau.eaufrance.fr`**, the
public API of the French ministry in charge of health, over HTTPS. Cleartext
traffic is forbidden by its network configuration. Requests are of three
kinds:

1. **Viewing a commune**: the list of its distribution units and the results
   of its latest sample.
2. **Viewing a history**: the results of one parameter, or the conclusions of
   the samples, over the chosen time window.
3. **Checking alerts**, if you turned them on: one light request per followed
   commune, at the chosen interval (weekly by default). Alerts stay off until
   you turn them on.

**What they reveal to Hub'Eau.** Like any request to any website, each one
shows Hub'Eau's server your **IP address** and the time of the request. It
also contains **the codes of the commune and of the distribution unit
requested** and, for a history, the parameter codes and the dates of the
window. It contains no identifier, no location, no name, nothing that
designates your device, apart from the generic `User-Agent` header of the
network library (`okhttp/<version>`), the same for every application that
uses it.

To be plain about it: if you turn alerts on, the periodic check asks Hub'Eau
about all your followed communes in a row, from the same IP address. Someone
with access to Hub'Eau's server logs could infer that one connection is
interested in those communes. That is the price of an application with no
server in between: no third party stands in the middle, but the source sees
who is talking to it. The use of those logs is governed by
[Hub'Eau's terms of use](https://hubeau.eaufrance.fr/page/conditions-generales).

The links of the About screen (Hub'Eau's terms, geo.api.gouv.fr, the licence
text, the source code, the Liberapay donation page) and the "Website" link of
the regional health agency on a commune's sheet (an `ars.sante.fr` site, or
the prefecture's site in Saint-Pierre-et-Miquelon) open in your browser, when
you ask for it; the application itself does not load them and contacts none
of those domains.

## Notifications

Alerts show local notifications, produced on the phone from Hub'Eau's data.
No remote (push) notification service is used: no server sends you anything.

## Backups

The application refuses Android's automatic backup
(`android:allowBackup="false"`) and, from Android 12, transfer to a new device
(extraction rules that exclude all of its storage). Nothing it stores, neither
the settings, nor the followed communes, nor their cache, goes into an online
backup or moves from one phone to another, even if backup is turned on on
your device.

The trade-off: on a new phone, or after reinstalling, communes and settings
have to be chosen again. The rest is only a cache of public data, which the
application downloads again.

To bring your communes elsewhere, **Settings → Backup** writes, when you ask
for it, a JSON file wherever you choose in the system file picker: the
followed communes (codes and names of the commune and of the distribution
unit), the unit chosen for each commune and the display settings (theme,
history period, retention period). It contains no location, no cache and no
alerts. That file only goes where you put it; to import, the application only
reads the file you point it to.

## Permissions, and what each one is for

| Permission | Use |
| --- | --- |
| `INTERNET` | The requests to `hubeau.eaufrance.fr` described above, and nothing else. |
| `ACCESS_COARSE_LOCATION` | "Near me": finding the nearest commune. Asked for at the time of use, optional. |
| `POST_NOTIFICATIONS` | Showing alerts (Android 13 and later). Asked for only when you turn an alert on. |
| `ACCESS_NETWORK_STATE` | Added by WorkManager: only run the alert check when a connection is available. |
| `RECEIVE_BOOT_COMPLETED` | Added by WorkManager: reschedule the alert check after a restart. |
| `WAKE_LOCK` | Added by WorkManager: let a running check finish. |

The AndroidX library also declares an internal permission,
`io.github.mgdx.potable.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, reserved to
the application itself; it grants access to nothing.

There is no permission for contacts, photos, files, microphone, camera,
precise location or advertising identifier. No foreground service, no exact
alarm, no battery optimisation exemption. The application works on a device
without Google services.

## Children

The application is meant for all audiences. It collects nothing, whatever the
age of the person using it.

## Changes to this policy

Any change is made in this file and in its French version, whose full history
is public in the repository. A change that would make the application collect
something would touch its founding principles: it would be announced in the
release notes, not slipped into a document.

## Contact

Questions and reports go to the
[issue tracker](https://github.com/mgdx/Potable/issues), which is public. A
security issue follows [`SECURITY.md`](../SECURITY.md).
