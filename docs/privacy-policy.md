# Politique de confidentialité

*Potable — `io.github.mgdx.potable`.*

<!-- Date d'entrée en vigueur à fixer à la première publication. -->

Potable ne collecte rien sur vous. Il n’y a ni compte, ni publicité, ni mesure
d’audience, ni rapport de plantage, ni identifiant unique, ni kit de
développement tiers qui pourrait en transporter un. Rien n’est envoyé à
l’auteur de l’application, qui ne détient donc aucune donnée sur qui que ce
soit et ne pourrait en remettre aucune si on la lui demandait.

Ce document dit ce qui se passe à la place, parce que « nous ne collectons
rien » est facile à écrire et ne vaut rien si on ne peut pas le vérifier.
L’application est un logiciel libre sous GNU GPL v3 : son
[code source](https://github.com/mgdx/Potable) fait référence, et chaque
affirmation ci-dessous peut y être vérifiée.

## Ce qui reste sur votre appareil

Trois choses sont écrites dans le stockage propre de l’application, qu’aucune
autre application ne peut lire :

- **Vos réglages** : l’acceptation de l’avertissement du premier lancement, le
  thème, la fenêtre d’historique, la durée de conservation du cache, les
  alertes et leur périodicité, et l’unité de distribution choisie pour chaque
  commune où vous en avez choisi une. « Vider le cache » oublie ce choix pour
  toutes les communes que vous ne suivez pas ; vider le cache d’une seule
  commune l’oublie pour elle si vous ne la suivez pas. S’y ajoutent les cinq
  dernières unités de distribution dont vous avez ouvert la fiche (codes et
  noms de la commune et de l’unité), proposées par l’écran de recherche ;
  chacune s’en retire d’un geste sur cet écran.
- **Vos communes suivies**, avec leur unité de distribution.
- **Le cache des données publiques des communes suivies et des cinq
  dernières consultées** : leur dernier prélèvement, les prélèvements
  ouverts depuis l’historique, les séries des paramètres consultés, la
  frise de leurs conclusions, et, pour les seules communes suivies, la date
  du dernier prélèvement vu par les alertes. Ce sont des données publiées par
  Hub’Eau, les mêmes pour tout le monde. Celles d’une unité de distribution
  sont effacées dès qu’elle n’est plus ni suivie ni parmi les communes
  consultées récemment (retirée de la liste, poussée hors de la liste par une
  nouvelle, ou retirée des communes suivies). Elles sont aussi purgées
  au-delà de la durée de conservation choisie (3 ans par défaut) ; dans les
  réglages, « Vider le cache » les efface toutes, et la liste des communes
  en cache permet de n’effacer que celles d’une commune.

Désinstaller l’application efface l’ensemble.

Le référentiel des communes embarqué dans l’application (noms, codes,
centroïdes) est un fichier public, en lecture seule, identique sur tous les
appareils.

## Ce qui n’est jamais enregistré

- **Votre position.**
- Les recherches que vous tapez.

## Votre position

La permission de localisation approximative (`ACCESS_COARSE_LOCATION`) n’est
demandée que lorsque vous touchez « Autour de moi », jamais au lancement. La
refuser ne bloque rien : la recherche par nom ou par code postal reste
disponible.

Si vous l’accordez, la position est lue auprès du service de localisation du
système Android, jamais auprès des services Google, que l’application
n’utilise pas. Elle sert **une fois**, sur le téléphone, à trouver la commune
dont le centre est le plus proche dans le référentiel embarqué, puis elle est
écartée. Elle n’est ni enregistrée, ni envoyée : aucune requête de
l’application ne la contient.

Selon votre appareil, le service de localisation du système peut lui-même
s’appuyer sur un fournisseur réseau de positionnement. C’est un réglage du
système, indépendant de Potable.

## Ce qui part sur le réseau

L’application ne contacte qu’**un seul domaine : `hubeau.eaufrance.fr`**,
l’API publique du ministère chargé de la santé, en HTTPS. Le trafic en clair
est interdit par sa configuration réseau. Les requêtes sont de trois sortes :

1. **Consulter une commune** : la liste de ses unités de distribution et les
   résultats de son dernier prélèvement.
2. **Consulter un historique** : les résultats d’un paramètre, ou les
   conclusions des prélèvements, sur la fenêtre de temps choisie.
3. **Vérifier les alertes**, si vous les avez activées : une requête légère
   par commune suivie, à la périodicité choisie (chaque semaine par défaut).
   Les alertes sont désactivées tant que vous ne les activez pas.

**Ce qu’elles révèlent à Hub’Eau.** Comme toute requête vers tout site web,
chacune montre au serveur de Hub’Eau votre **adresse IP** et l’heure de la
demande. Elle contient en outre **les codes de la commune et de l’unité de
distribution demandées**, et, pour un historique, les codes des paramètres et
les dates de la fenêtre. Elle ne contient ni identifiant, ni position, ni nom,
ni rien qui désigne votre appareil, à part l’en-tête `User-Agent` générique de
la bibliothèque réseau (`okhttp/<version>`), le même pour toutes les
applications qui l’emploient.

Il faut le dire clairement : si vous activez les alertes, la vérification
périodique interroge Hub’Eau sur toutes vos communes suivies à la suite, depuis
la même adresse IP. Quelqu’un qui aurait accès aux journaux du serveur de
Hub’Eau pourrait en déduire qu’une même connexion s’intéresse à ces communes.
C’est le prix d’une application sans serveur intermédiaire : aucun tiers ne
s’interpose, mais la source voit qui lui parle. L’utilisation de ces journaux
relève des
[conditions générales d’utilisation de Hub’Eau](https://hubeau.eaufrance.fr/page/conditions-generales).

Les liens de l’écran « À propos » (conditions de Hub’Eau, geo.api.gouv.fr,
texte de la licence, code source, page de dons Liberapay) s’ouvrent dans votre navigateur, à votre
demande ; l’application elle-même ne les charge pas.

## Notifications

Les alertes affichent des notifications locales, produites sur le téléphone à
partir des données de Hub’Eau. Aucun service de notification à distance
(push) n’est utilisé : aucun serveur ne vous envoie quoi que ce soit.

## Sauvegardes

L’application refuse la sauvegarde automatique d’Android
(`android:allowBackup="false"`) et, à partir d’Android 12, le transfert vers
un nouvel appareil (règles d’extraction qui excluent tout son stockage). Rien
de ce qu’elle enregistre, ni les réglages, ni les communes suivies, ni leur
cache, ne part dans une sauvegarde en ligne ni ne passe d’un téléphone à
l’autre, même si la sauvegarde est activée sur votre appareil.

La contrepartie : sur un nouveau téléphone, ou après une réinstallation, il
faut de nouveau choisir ses communes et ses réglages. Le reste n’est qu’un
cache de données publiques, que l’application télécharge à nouveau.

## Permissions, et à quoi chacune sert

| Permission | Usage |
| --- | --- |
| `INTERNET` | Les requêtes à `hubeau.eaufrance.fr` décrites plus haut, et rien d’autre. |
| `ACCESS_COARSE_LOCATION` | « Autour de moi » : trouver la commune la plus proche. Demandée au moment de l’usage, facultative. |
| `POST_NOTIFICATIONS` | Afficher les alertes (Android 13 et plus). Demandée seulement quand vous activez une alerte. |
| `ACCESS_NETWORK_STATE` | Ajoutée par WorkManager : ne lancer la vérification des alertes que si une connexion est disponible. |
| `RECEIVE_BOOT_COMPLETED` | Ajoutée par WorkManager : replanifier la vérification des alertes après un redémarrage. |
| `WAKE_LOCK` | Ajoutée par WorkManager : laisser une vérification en cours se terminer. |

La bibliothèque AndroidX déclare en outre une permission interne,
`io.github.mgdx.potable.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, réservée à
l’application elle-même ; elle ne donne accès à rien.

Il n’y a aucune permission pour les contacts, les photos, les fichiers, le
micro, l’appareil photo, la localisation précise ou l’identifiant
publicitaire. Aucun service au premier plan, aucune alarme exacte, aucune
exemption d’optimisation de batterie. L’application fonctionne sur un appareil
sans services Google.

## Enfants

L’application s’adresse à tous les publics. Elle ne collecte rien, quel que
soit l’âge de la personne qui l’utilise.

## Modifications de cette politique

Toute modification est faite dans ce fichier, dont l’historique complet est
public dans le dépôt. Un changement qui ferait collecter quelque chose à
l’application toucherait à ses principes fondateurs (SPEC §2 et §10) : il
serait annoncé dans les notes de version, pas glissé dans un document.

## Contact

Les questions et signalements vont dans le
[gestionnaire d’issues](https://github.com/mgdx/Potable/issues), qui est
public. Un problème de sécurité suit [`SECURITY.md`](../SECURITY.md).
