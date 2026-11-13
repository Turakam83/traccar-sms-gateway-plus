# Traccar SMS Gateway Plus

Cette variante locale part de Traccar `v7.0.4` (`216a8ae363ca00ba03be2ecd1ed33a28c30f4c4d`). Son identifiant `org.traccar.gateway.redmi` permet de garder la version officielle installée avec sa configuration et son accès cloud.

## Installer et sauvegarder

1. Dans la passerelle officielle, relève la clé du service local et l’adresse HTTP utilisée par ton logiciel. Conserve la clé dans un emplacement privé. Si tu souhaites une copie de tes SMS, utilise la fonction d’export des messages de Traccar.
2. Désactive le service local dans la version officielle pour libérer le port `8082`. Installe l’APK **Traccar SMS Gateway Plus**. Par USB, utilise `adb install -r chemin-vers-apk` après avoir autorisé le débogage sur le Redmi.
3. Ouvre la variante, choisis-la comme application SMS par défaut et accorde les permissions demandées. Dans **Gateway**, utilise **Importer la clé HTTP** pour remettre la clé officielle, puis **Activer le service**. L’état **Serveur HTTP prêt** indique que le port répond sur le téléphone.
4. Dans les réglages MIUI de cette application, autorise le **démarrage automatique** et sélectionne **Sans restriction** dans les réglages de batterie. Les noms et chemins de ces réglages varient selon la version de MIUI. Après un redémarrage, déverrouille le téléphone une première fois.

L’adresse HTTP reste utilisable sur le réseau local, avec le port `8082`. Garde ce port sur le réseau privé. Android et MIUI peuvent suspendre le réseau en veille ou empêcher une relance en arrière-plan. Après **Forcer l’arrêt** dans Android, ouvre à nouveau l’écran de la passerelle pour reprendre. Après une désactivation dans l’application, le service reste arrêté, y compris au redémarrage du téléphone.

## Configurer le fonctionnement en arrière-plan

Dans la configuration de la passerelle, touche **Configurer le fonctionnement en arrière-plan**, sous **Journal de diagnostic**. Android demande une exemption d’optimisation de la batterie si elle est inactive. Au retour, l’application vérifie son état.

Sur Xiaomi, le parcours ouvre aussi les réglages **Sans restriction** et **Démarrage automatique**, avec la fiche de l’application comme solution de repli. Confirme ces deux réglages dans MIUI ou passe une étape. Sur les autres appareils, la fiche Android permet de vérifier les réglages de batterie propres au téléphone.

## Envoyer un SMS et consulter son résultat

`POST /` accepte toujours l’en-tête `Authorization` avec la clé brute et le JSON `to`, `message`, `slot` facultatif. La numérotation des emplacements SIM commence à `0`. Envoie le texte intégral dans `message`. Android choisit le découpage selon l’encodage et la SIM. Le `200` immédiat à corps vide indique que la demande a été soumise à Android.

Une réponse qui contient `X-SMS-Request-Id` permet de consulter `GET /sms/status/{id}` avec la même clé. Exemple de réponse après un envoi confirmé par Android sur deux segments.

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "status": "sent",
  "totalSegments": 2,
  "segments": [{"index": 1, "resultCode": -1}, {"index": 2, "resultCode": -1}]
}
```

`pending` indique qu’il manque des résultats. `sent` indique que tous les segments ont un retour Android positif. `failed` indique un échec de soumission ou de tous les segments. `partial` indique que des segments ont réussi et que l’envoi complet a échoué. `unknown` indique que des callbacks manquent toujours après deux minutes. Les résultats sont consultables pendant 24 heures, et un retour tardif peut compléter un résultat indéterminé pendant ce délai. `errorCode` et `noDefault` sont inclus lorsque le modem ou Android les fournit. Le statut renseigne l’envoi côté Android. Le regroupement se constate dans l’application du destinataire.

Quand la variante est l’application SMS par défaut, la conversation affiche le texte entier dans une seule entrée, dès la soumission. La ligne passe à **Envoyé** après tous les retours positifs, ou à **Échec** sur un échec confirmé. En cas de résultat indéterminé, la ligne peut rester en attente. Lorsque la variante n’a pas le rôle SMS, Android gère l’écriture dans la base des messages. Le **Journal de diagnostic** aide à retrouver les résultats et redémarrages par identifiant, sans conserver les textes, numéros ou clés.

## Vérifier sur le Redmi

1. Avec la version officielle, relève la version Android et MIUI, puis teste un texte GSM-7 de 160 caractères et un autre de 161 caractères. Compare un texte Unicode long envoyé par API et manuellement. Utilise la même SIM et le même destinataire à chaque fois. Relève le code HTTP, la présence dans la conversation locale et la réception. Avec deux SIM, compare la sélection par défaut et le `slot` utilisé par ton logiciel.
2. Refais les mêmes tests avec la variante. Compare les codes par segment, le nombre de lignes dans la conversation et le texte reçu. Si Android indique `sent` mais que le destinataire ne reçoit rien ou reçoit des fragments séparés, compare avec l’envoi manuel sur cette même SIM. Le journal est lisible dans l’application ou via `adb logcat -s TraccarRedmi`.
3. Teste un échec radio et le statut correspondant. Redémarre ensuite le téléphone avec le service activé, déverrouille-le et vérifie le `GET /` depuis un autre appareil du réseau local. Vérifie également après plusieurs heures écran éteint et après un arrêt involontaire. Distingue ces essais d’un **Forcer l’arrêt**, qui relève du système.
4. Vérifie qu’un port déjà occupé donne un état de reprise dans le journal, et que le serveur démarre une fois le port libéré. Désactive ensuite la passerelle depuis l’interface et redémarre le téléphone pour vérifier qu’elle reste désactivée.

## Vérifications effectuées

- Sur deux émulateurs Android 11, l’APK officiel a livré un SMS de 174 caractères en un message, sans créer de ligne côté expéditeur lorsqu’il détenait le rôle SMS. La variante Redmi a livré les SMS GSM-7 de 160 et 161 caractères avec respectivement un et deux segments confirmés. Pour chaque envoi, le texte exact figurait dans une seule entrée côté expéditeur et côté destinataire.
- Le texte Unicode long avec accents et idéogrammes a été reçu à l’identique. Un test contenant des emojis a montré une altération à la réception. L’injection directe de SMS via le simulateur altérait elle aussi les caractères hors du plan multilingue de base. La fidélité de ces caractères reste à vérifier sur un téléphone réel.
- En mode avion, l’API a signalé `failed` et la base Android contenait une ligne en échec. La passerelle a repris après l’arrêt de son processus, un redémarrage de l’émulateur, une mise à jour de l’APK et la libération du port `8082`. Une désactivation volontaire a persisté après redémarrage.
- Les 13 tests automatisés, Android Lint et la compilation de la variante Traccar habituelle ont abouti. Le Redmi physique n’a pas été détecté par ADB. Le défaut initial de réception sur ce téléphone et la disponibilité prolongée du réseau en veille sous MIUI restent à reproduire.

## Revenir à la version officielle

Désactive le service de la variante. Dans les réglages Android, rétablis l’application SMS par défaut précédente, puis réactive le service local dans la version officielle. L’URL et la clé de l’originale sont conservées. Les SMS écrits dans la base Android restent dans les conversations. Tu peux ensuite désinstaller la variante.

## Compiler et exécuter les tests

Le projet utilise un JDK 17, le SDK Android 36 et le wrapper Gradle fourni. Configure `JAVA_HOME` et `ANDROID_HOME`, puis lance sous Windows.

```powershell
.\gradlew.bat -PredmiLocal=true :app:testRedmiDebugUnitTest :app:assembleRedmiDebug
.\gradlew.bat -PredmiLocal=true :app:assembleRedmiRelease
```

Les APK Gradle sont sous `app/build/outputs/apk/redmi`. Le build `debug` reçoit la signature de débogage Android. Pour distribuer le build `release`, utilise une clé de signature dédiée et vérifie le résultat avec `apksigner verify`. Garde la même clé pour installer les mises à jour par-dessus cette variante. La compilation Traccar habituelle reste disponible sans `-PredmiLocal=true`.
