Rijksdriehoekscoördinaten
=========================

Met deze app is het kinderlijk eenvoudig om Nederlandse coördinaten op de kaart op te zoeken. Geen gedoe meer met topografische kaarten en kaarthoekmeters, maar gewoon direct resultaat.

**2025 update:** deze app is gebouwd voor oudere versies van Android. De app werkt nog steeds op Android 15, maar het OS weigert nieuwe installaties. Het systeem claimt dat het niet ondersteunt is en niet zal werken. Dit is een leugen. Er is een omweg, via ADB op een pc:

    adb install --bypass-low-target-sdk-block Release/Rijksdriehoekscoordinaten.apk

Je kan coördinaten op de kaart zoeken, maar ook invullen in het tekstvak. Het is ook mogelijk om gelijk de huidige locatie weer te geven.

Het coördinatenstelsel heet Rijksdriehoek, en wordt onderhouden door het Kadaster. Deze coördinaten worden gebruikt op kaarten van de Topografische Dienst. Bij scoutinggroepen zijn deze kaarten bekend als stafkaart.

![Screenshot](/Art/JB-nexus4-landscape-luchtfoto-meridiaan_framed_medium.png)

Meer informatie: [Wikipedia](http://nl.wikipedia.org/wiki/Rijksdriehoekscoördinaten) en [Scoutpedia](http://nl.scoutwiki.org/Rijksdriehoeksgrid).

Deze app maakt gebruik van de [Proj4j](http://trac.osgeo.org/proj4j/) library van OSGeo.

---

Rijksdriehoek coordinates
=========================

This app makes it very simple to look up coordinates using the Dutch RD grid or Rijksdriehoek system. No hassle with romers and large paper maps, but direct results.

**2025 update:** this app is built for older versions of Android. The app still works on Android 15, but the OS won't allow new installations. The system claims it is unsupported and will not work. This is a lie. There is a workaround, which requires using ADB on a pc:

    adb install --bypass-low-target-sdk-block Release/Rijksdriehoekscoordinaten.apk

You can lookup coordinates on the map, and you can also enter them manually. It is also possible to find your current location.

The coordinate system is called Rijksdriehoek, and is maintained by the Dutch Cadastre. These coordinates are used on their maps, and Dutch scouting groups use these maps very extensively.

This app uses the [Proj4j](http://trac.osgeo.org/proj4j/) library from OSGeo.
