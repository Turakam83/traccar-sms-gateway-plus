# Traccar SMS Gateway Plus

[Français](#français) · [English](#english) · [APK](https://github.com/Turakam83/traccar-sms-gateway-plus/releases/latest)

Fork communautaire / Community fork of [Traccar SMS Gateway](https://github.com/traccar/traccar-sms-gateway), based on [v7.0.4](https://github.com/traccar/traccar-sms-gateway/tree/v7.0.4). Traccar SMS Gateway is based on [Fossify Messages](https://github.com/FossifyOrg/Messages).

## Français

Traccar SMS Gateway Plus transforme un téléphone Android en passerelle SMS sur le réseau local. Il utilise la SIM du téléphone et reste compatible avec les appels HTTP existants.

- Envoie un SMS long ou contenant des émojis en une seule requête. Android découpe le texte en segments que le téléphone destinataire peut regrouper.
- Affiche les SMS envoyés via l’API dans les conversations. L’en-tête `X-SMS-Request-Id` permet de consulter le résultat Android par segment.
- Relance la passerelle après un redémarrage ou une interruption lorsque le système l’autorise. Le journal de diagnostic aide à suivre les reprises et les erreurs. L’assistant ouvre les réglages Android et MIUI pour le fonctionnement en arrière-plan.
- Permet de marquer tous les messages comme lus. Les réactions SMS reconnues s’affichent en bulles quand un seul message correspond au texte cité. Si plusieurs SMS correspondent, la réaction reste visible comme SMS.

Télécharge l’APK signé depuis les [releases](https://github.com/Turakam83/traccar-sms-gateway-plus/releases/latest). Ouvre le fichier sur le téléphone et autorise l’installation depuis cette source si Android le demande. Choisis Plus comme application SMS par défaut pour utiliser ses fonctions de messagerie et d’historique.

### Passer de la version officielle à Plus

1. Dans l’application officielle, relève l’URL et la clé du **service local**. Tu peux exporter tes conversations si tu souhaites en garder une copie.
2. Désactive le service local de l’application officielle pour libérer le port `8082`. Dans la configuration de la passerelle Plus, choisis **Importer la clé HTTP**, colle la clé relevée, puis active le service. Attends l’état **Serveur HTTP prêt**.
3. Depuis ton logiciel, utilise l’URL locale du téléphone sur le port `8082` et la clé importée. Si l’adresse IP du téléphone a changé, mets à jour l’URL. Conserve la clé dans un emplacement privé et utilise la passerelle sur ton réseau local.

Les deux applications peuvent cohabiter. Pour revenir à l’officielle, désactive la passerelle Plus, rétablis l’application SMS par défaut de ton choix et réactive le service local de l’officielle. Si ton logiciel utilisait le **service cloud** de Traccar, conserve l’application officielle pour ce service ou reconfigure ton logiciel pour qu’il puisse joindre la passerelle HTTP locale de Plus.

### Adapter le code qui envoie les SMS

- Pour un envoi simple, conserve `POST /`, la clé brute dans l’en-tête `Authorization` et le JSON contenant les champs `to` et `message`. Envoie une requête par destinataire. Le champ numérique facultatif `slot` sélectionne la SIM à partir de `0`. En son absence, Android utilise la SIM SMS par défaut.
- Si ton logiciel découpait les textes à 160 caractères ou retirait les accents, retire ces traitements du chemin d’envoi local. Transmets le texte complet dans `message`. Utilise une bibliothèque JSON ou une fonction d’échappement JSON pour conserver les guillemets et les sauts de ligne. Encode la requête en UTF‑8 pour préserver les accents et les émojis. Android gère la segmentation du SMS.
- Un `200 OK` à corps vide indique que la demande a été soumise à Android. Si ton logiciel affiche un résultat d’envoi, lis l’en-tête `X-SMS-Request-Id` lorsqu’il est présent, puis interroge `GET /sms/status/{id}` avec la même clé. Les états et les exemples figurent dans la section [API HTTP](#api-http).

Teste la migration depuis ton logiciel avec un SMS court, puis un texte de plus de 160 caractères contenant des accents, des émojis et un saut de ligne. Vérifie le texte reçu et, si tu utilises le suivi, le statut Android. Sur un téléphone à deux SIM, vérifie aussi le `slot` configuré.

Dans la configuration de la passerelle, **Configurer le fonctionnement en arrière-plan** ouvre les réglages de batterie. Sur Xiaomi, vérifie **Sans restriction** et **Démarrage automatique**. Après un redémarrage, déverrouille le téléphone une première fois. Après un **Forcer l’arrêt** dans Android, rouvre Plus pour reprendre. Le **Journal de diagnostic** indique les reprises et les erreurs.

## English

Traccar SMS Gateway Plus turns an Android phone into a local network SMS gateway. It uses the phone’s SIM and supports existing HTTP clients.

- Send a long SMS or a message with emoji in one request. Android splits the text into segments the receiving phone can reassemble.
- See messages sent through the API in your conversations. Use the `X-SMS-Request-Id` response header to look up Android’s results for each segment.
- Restart the gateway after a reboot or interruption when the system allows it. The diagnostic log records recovery and errors. The setup assistant opens the Android and MIUI settings for background operation.
- Mark all messages as read. Recognized SMS reactions appear as bubbles when the quoted text matches a single message. If several messages match, the reaction stays visible as an SMS.

Download the signed APK from [releases](https://github.com/Turakam83/traccar-sms-gateway-plus/releases/latest). Open the file on your phone and allow installation from that source if Android asks. Set Plus as the default SMS app to use its messaging and history features.

### Switch from the official app to Plus

1. In the official app, record the **local service** URL and HTTP key. You can export your conversations if you want a backup.
2. Turn off the official app’s local service to free port `8082`. In the Plus gateway settings, select **Import the HTTP key**, paste the key, and enable the service. Wait for **HTTP server ready**.
3. In your client, use the phone’s local URL on port `8082` and the imported key. Update the URL if the phone’s IP address has changed. Store the key privately and use the gateway on your local network.

Both apps can be installed side by side. To switch back, turn off the Plus gateway, restore your preferred default SMS app, and enable the official app’s local service again. If your software used the Traccar **cloud service**, keep the official app for that service or reconfigure your software to reach the local HTTP gateway in Plus.

### Update your SMS client

- For a basic send, keep `POST /`, the raw key in the `Authorization` header, and the `to` and `message` JSON fields. Send one request per recipient. The optional numeric `slot` field selects a SIM slot starting at `0`. When omitted, Android uses the default SMS SIM.
- If your software split messages at 160 characters or stripped accents, remove those steps from the local send path. Send the full text in `message`. Use a JSON library or JSON escaping helper to preserve quotes and line breaks. Encode the request as UTF-8 to preserve accents and emoji. Android handles SMS segmentation.
- An empty `200 OK` means the request was submitted to Android. If your client reports send results, read the `X-SMS-Request-Id` response header when present and query `GET /sms/status/{id}` using the same key. See [HTTP API](#api-http) for examples and result meanings.

Test from your client with a short SMS, then with a message longer than 160 characters containing accented text, emoji, and a line break. Check the received text and, if you use status tracking, the Android send result. On dual-SIM phones, also check the configured `slot`.

In the gateway settings, **Configure background operation** opens battery settings. On Xiaomi, check **No restrictions** and **Background autostart**. Unlock the phone once after rebooting. After an Android **Force stop**, reopen Plus to resume. The **Diagnostic journal** records recovery and errors.

<a id="api-http"></a>

## API HTTP / HTTP API

Exemple de requête / Example request

```http
POST / HTTP/1.1
Host: 192.0.2.10:8082
Authorization: YOUR_LOCAL_HTTP_KEY
Content-Type: application/json; charset=utf-8

{"to":"+12025550101","message":"Réservation confirmée à 19 h 😊\nÀ bientôt !"}
```

Remplace l’adresse d’exemple, la clé et le numéro. La clé se transmet seule dans `Authorization`, sans préfixe `Bearer`.

Replace the example address, key, and recipient number. Send the key by itself in `Authorization`, without a `Bearer` prefix.

Suivi facultatif / Optional status lookup

```http
GET /sms/status/00000000-0000-0000-0000-000000000000 HTTP/1.1
Host: 192.0.2.10:8082
Authorization: YOUR_LOCAL_HTTP_KEY
```

Remplace l’identifiant par la valeur de `X-SMS-Request-Id`. L’identifiant peut aussi être présent sur un `500` si Plus a enregistré la demande avant l’échec.

Replace the example ID with the value of `X-SMS-Request-Id`. A `500` can also include an ID if Plus recorded the request before it failed.

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "status": "sent",
  "totalSegments": 2,
  "segments": [
    {"index": 1, "resultCode": -1},
    {"index": 2, "resultCode": -1}
  ]
}
```

| Statut / Status | Français | English |
| --- | --- | --- |
| `pending` | Android doit encore retourner un résultat. | Android still has results to report. |
| `sent` | Android a confirmé l’envoi de tous les segments. | Android reported success for every segment. |
| `failed` | La soumission a échoué ou tous les segments ont échoué. | Submission failed or every segment failed. |
| `partial` | Android a confirmé certains segments seulement et l’envoi complet a échoué. | Android reported success for some segments and the complete send failed. |
| `unknown` | Des résultats manquent après deux minutes. Un retour tardif peut encore mettre à jour le statut. | Results are missing after two minutes. A late result can still update the status. |

Les résultats restent consultables pendant 24 heures. Un `404` sur le suivi indique un identifiant inconnu ou expiré. Le `resultCode` Android vaut `-1` en cas de réussite. Les champs `errorCode` et `noDefault` peuvent préciser un échec. Le statut `sent` concerne l’envoi côté Android. La réception se vérifie sur le téléphone destinataire.

Results are available for 24 hours. A `404` on the status endpoint means the ID is unknown or expired. An Android `resultCode` of `-1` means success. The optional `errorCode` and `noDefault` fields can give more detail about failures. The `sent` status reports Android’s send result. Check the receiving phone to confirm delivery.

Sur le `POST`, un `401` indique une clé absente ou incorrecte, un `400` un JSON ou un `slot` mal formé, et un `500` un champ requis manquant ou un échec de soumission. Après un délai d’attente HTTP, vérifie l’historique ou le journal avant de renvoyer le message pour éviter un doublon.

On the `POST`, `401` means the key is missing or incorrect, `400` means the JSON or `slot` is malformed, and `500` means a required field is missing or submission failed. After an HTTP timeout, check message history or the diagnostic journal before resending to avoid duplicates.

## Build / Compilation

Use JDK 17, Android SDK 36 and the included Gradle wrapper.

```sh
./gradlew -PredmiLocal=true :app:testRedmiDebugUnitTest :app:assembleRedmiRelease
```

On Windows, run `gradlew.bat` with the same arguments. Find the APK under `app/build/outputs/apk/redmi/release`. Sign locally built APKs with your own key. The APK from this repository’s releases uses the same signing certificate as the previously distributed Modif build.

## Upstream maintainer / Auteur du projet d’origine

- Anton Tananaev ([anton@traccar.org](mailto:anton@traccar.org))

## License

    GNU General Public License, Version 3

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
