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

Avant de passer de la version officielle à Plus, relève la clé HTTP et l’adresse utilisée par ton logiciel. Tu peux aussi exporter tes conversations depuis l’application. Les deux versions peuvent cohabiter. Désactive le service local de l’officielle pour libérer le port `8082`, puis importe sa clé HTTP et active la passerelle dans Plus. Pour revenir à l’officielle, désactive la passerelle dans Plus, rétablis l’application SMS par défaut souhaitée et réactive le service local de l’officielle.

[Guide détaillé en français](README-Redmi.md) pour l’installation, les réglages Android et MIUI et les résultats d’envoi.

## English

Traccar SMS Gateway Plus turns an Android phone into a local network SMS gateway. It uses the phone’s SIM and supports existing HTTP clients.

- Send a long SMS or a message with emoji in one request. Android splits the text into segments the receiving phone can reassemble.
- See messages sent through the API in your conversations. Use the `X-SMS-Request-Id` response header to look up Android’s results for each segment.
- Restart the gateway after a reboot or interruption when the system allows it. The diagnostic log records recovery and errors. The setup assistant opens the Android and MIUI settings for background operation.
- Mark all messages as read. Recognized SMS reactions appear as bubbles when the quoted text matches a single message. If several messages match, the reaction stays visible as an SMS.

Download the signed APK from [releases](https://github.com/Turakam83/traccar-sms-gateway-plus/releases/latest). Open the file on your phone and allow installation from that source if Android asks. Set Plus as the default SMS app to use its messaging and history features.

Before switching from the official app, save the HTTP key and the gateway address used by your client. You can also export your conversations from the app. Both versions can be installed side by side. Turn off the official app’s local service to free port `8082`, then import its HTTP key and enable the gateway in Plus. To switch back, turn off the Plus gateway, restore your preferred default SMS app, and enable the official app’s local service again.

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
