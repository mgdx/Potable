# Signaler un problème de sécurité

Potable n’a ni compte, ni clé, ni serveur à lui. Une faille, ici, porte
presque toujours sur **ce qui sort du téléphone**, ou sur **ce qu’une autre
application peut lui faire faire**. C’est pour ces signalements-là que cette
page existe.

## Ce qui entre dans le périmètre

- Tout ce qui fait **envoyer** à l’application ce qu’elle promet de ne jamais
  envoyer : une position, un identifiant, la liste des communes suivies — ou
  n’importe quelle requête vers un autre hôte que `hubeau.eaufrance.fr`.
- Tout ce qui affaiblit le transport : une requête en clair, un certificat
  non vérifié, une redirection suivie hors de Hub’Eau.
- Tout ce qui permet à **une autre application de l’appareil** de lire ce que
  Potable conserve, ou de lui faire ouvrir ou afficher autre chose que ce
  qu’il a lui-même choisi : un composant exporté, un intent, une notification.
- Une réponse de l’API, même hostile, qui fait planter l’application ou lui
  fait faire plus que l’afficher.
- Une dépendance embarquée dans l’APK avec une vulnérabilité connue que
  l’application atteint réellement.

## Ce qui n’en relève pas

- L’API Hub’Eau elle-même, son site ou ses données : à signaler à
  [Hub’Eau](https://hubeau.eaufrance.fr/). Une donnée publiée erronée relève de
  l’ARS concernée.
- Une fonctionnalité absente, si prudente soit-elle : c’est une issue, pas une
  vulnérabilité.
- Ce qui suppose un appareil déjà compromis, déverrouillé et entre les mains
  de l’attaquant, ou une compilation que le projet n’a pas produite.
- Le rapport d’un scanner transmis sans chemin d’exploitation à travers ce
  code.

## Comment signaler

**Utilisez le formulaire d’avis de sécurité privé de GitHub :
<https://github.com/mgdx/Potable/security/advisories/new>.** C’est un canal
privé entre vous et les mainteneurs, qui garde le signalement et sa discussion
au même endroit.

Si ce formulaire ne vous est pas accessible, ouvrez une issue publique qui dit
**seulement** que vous avez un signalement de sécurité et comment vous joindre
— sans aucun détail — et un moyen privé vous sera proposé.

Ce qui aide, dans l’ordre : la version et le mode d’installation, l’appareil
et sa version d’Android, ce que l’attaquant doit pouvoir faire au préalable,
et la plus courte séquence qui montre le problème. Un correctif est bienvenu,
jamais attendu.

## Ce qui se passe ensuite

- **Sous 5 jours**, un accusé de réception par un humain.
- **Sous 30 jours**, une évaluation : s’agit-il d’une faille, de quelle
  gravité, et quel correctif envisager.
- Le correctif est publié dès qu’il est prêt, avec l’avis de sécurité.
  **90 jours** après le signalement, le problème est rendu public, corrigé ou
  non : une faille tenue secrète indéfiniment ne protège que le projet.
- Vous êtes crédité sous le nom que vous choisissez, ou pas du tout si vous
  préférez. Il n’y a pas de prime : l’application n’a aucun revenu pour en
  payer.

## Bon à savoir avant d’écrire

**L’application n’envoie aucun journal.** Pas de rapport de plantage, pas de
télémétrie : rien de votre problème ne nous parvient tout seul. Si vous
joignez un journal capturé avec `adb`, relisez-le d’abord.

**Aucun correctif ne sera jamais livré par un canal à part.** Potable se met à
jour par le magasin ou la page de versions d’où il a été installé, et par
aucun autre moyen ; il ne télécharge jamais de code. Quiconque prétend le
contraire n’est pas nous.
