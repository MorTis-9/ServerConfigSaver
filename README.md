# ServerConfigSaver

**ServerConfigSaver** is a client-side Fabric mod that keeps separate Minecraft options and mod configuration files for each multiplayer server. Profiles are stored locally and selected automatically when you join a server.

## Features

- Saves a separate profile for each multiplayer server address and port.
- Profiles vanilla client options such as sound, video, controls, language, and accessibility settings.
- Copies configuration files stored in `.minecraft/config` and root-level `options*.txt` files into the active server profile.
- Watches supported config files during a session and mirrors completed disk writes to the active profile.
- Lets you enable or disable profiles, reset the active profile to the initial shared-settings snapshot, or copy the active profile to another server profile.
- Restores shared settings after disconnect. If the game crashes during a server session, it attempts to restore the shared snapshot on next launch.
- Offers a global sync switch that copies future Minecraft and profile-managed config changes to every server profile and shared settings.
- Leaves resource-pack handling to Minecraft's normal behavior.
- Includes dedicated reload integrations for Reach Display, Zoomify, Health Indicators, and Combat Hitboxes.
- Provides English, Turkish, and Spanish translations, including English and Spanish regional language variants.

## Compatibility and limitations

- Minecraft Java Edition **1.21.11**
- **Fabric**; client-side only
- Requires Fabric Loader **0.19.5 or newer**, Fabric API, and Java 21 or newer.

ServerConfigSaver profiles files saved under `.minecraft/config` and root-level `options*.txt` files. It includes reload integrations for several popular mods and config libraries, but cannot guarantee support for every mod: some store settings elsewhere, cache them in memory, or do not expose a reload method. Such settings are still saved when their files are profile-managed, but may require restarting the game or may not take effect until the mod is supported. Gamma Utils has no special runtime integration. Resource packs are intentionally excluded.

Profiles and the shared-settings snapshot are stored in `.minecraft/serverbound/`.

## Use

- Press **O** to open the profile screen. The key can be changed in Minecraft's Controls menu.
- Use the button at the bottom-right of the title screen or near the bottom-right of the multiplayer screen to enable or disable server profiles.
- Press **O** and enable **Sync changes to all profiles** when a change should also update every server profile and shared settings.
- The profile screen lets you reset the active server profile or copy it to another server. Enter the server address; a custom port is optional (for example `play.example.net:25566`). If omitted, port `25565` is used.
- Client commands: `/serverbound status`, `/serverbound toggle`, `/serverbound reset`, and `/serverbound copy <destination>`.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11 and Fabric API.
2. Put the ServerConfigSaver `.jar` file in the `mods` folder for the Minecraft installation you use.
3. Launch the game with that Fabric profile.

## Build from source

Install Java 21, then run `gradlew.bat build` on Windows or `./gradlew build` on macOS/Linux. The distributable mod JAR is created in `build/libs/`; do not use the `-sources.jar` file as the mod.

## License

ServerConfigSaver is licensed under the MIT License. See [LICENSE](LICENSE) for the full text.

---

# Türkçe — ServerConfigSaver

**ServerConfigSaver**, her çok oyunculu sunucu için ayrı Minecraft ayarları ve mod yapılandırma dosyaları tutan istemci taraflı bir Fabric modudur. Profiller bilgisayarda saklanır ve sunucuya girince otomatik seçilir.

## Özellikler

- Sunucu adresi ve portuna göre ayrı profil oluşturur.
- Ses, görüntü, kontroller, dil ve erişilebilirlik gibi Minecraft istemci ayarlarını profiller.
- `.minecraft/config` içindeki dosyaları ve `.minecraft` kökündeki `options*.txt` dosyalarını etkin sunucu profiline kopyalar.
- Oyun sırasında desteklenen dosyalardaki değişiklikleri izler ve diske yazma tamamlanınca profil kopyasını günceller.
- Profilleri açıp kapatmaya, etkin profili ortak ayarların ilk yedeğine sıfırlamaya ve etkin profili başka sunucu profiline kopyalamaya izin verir.
- Sunucudan çıkınca ortak ayarları geri yükler. Oyun sunucudayken çökerse sonraki açılışta yedeği geri yüklemeyi dener.
- Tüm profillere eşitleme düğmesi, sonraki Minecraft ve desteklenen mod ayarı değişikliklerini tüm sunucu profillerine ve ortak ayarlara kopyalar.
- Kaynak paketlerini Minecraft'ın normal yönetimine bırakır.
- Reach Display, Zoomify, Health Indicators ve Combat Hitboxes için özel yeniden yükleme desteği içerir. Gamma Utils'a özel çalışma zamanı müdahalesi yapmaz.
- İngilizce, Türkçe ve İspanyolca çevirileri; İngilizce ve İspanyolcanın bölgesel dil seçenekleriyle birlikte sunar.

## Uyumluluk ve sınırlar

- Minecraft Java Edition **1.21.11**
- **Fabric**, yalnızca istemci
- Fabric Loader **0.19.5+**, Fabric API ve Java 21 veya yenisi gerekir.

ServerConfigSaver, `.minecraft/config` içine ve kökteki `options*.txt` dosyalarına kaydedilen ayarları profiller. Bazı yaygın modlar ve ayar kütüphaneleri için yeniden yükleme desteği vardır; ancak her mod garanti edilemez. Bazı modlar ayarlarını başka yerde tutar, bellekte önbelleğe alır veya yeniden yükleme yöntemi sunmaz. Bu dosyalar kaydedilse bile oyunu yeniden başlatmak gerekebilir ya da mod henüz desteklenmiyor olabilir. Kaynak paketleri kapsam dışıdır.

Profiller ve ortak ayar yedeği `.minecraft/serverbound/` altında saklanır.

## Kullanım

- Profil ekranını açmak için **O** tuşuna bas. Tuşu Minecraft'ın Kontroller menüsünden değiştirebilirsin.
- Ana menünün sağ altındaki veya çok oyunculu ekranının sağ alt tarafındaki düğmeyle sunucu profillerini açıp kapatabilirsin.
- **O** tuşuna basıp **Değişiklikleri tüm profillere uygula** seçeneğini açarsan sonraki değişiklikler tüm sunuculara ve ortak ayarlara da kopyalanır.
- Profil ekranından etkin profili sıfırlayabilir veya başka sunucuya kopyalayabilirsin. Sunucu adresini gir; özel port isteğe bağlıdır (ör. `play.example.net:25566`). Port girmezsen `25565` kullanılır.
- İstemci komutları: `/serverbound status`, `/serverbound toggle`, `/serverbound reset` ve `/serverbound copy <hedef>`.

## Kurulum

1. Minecraft 1.21.11 için Fabric Loader ve Fabric API'yi kur.
2. ServerConfigSaver `.jar` dosyasını kullandığın Minecraft kurulumunun `mods` klasörüne koy.
3. Oyunu ilgili Fabric profiliyle başlat.

## Kaynak koddan derleme

Java 21 kur ve Windows'ta `gradlew.bat build`, macOS/Linux'ta `./gradlew build` çalıştır. Mod JAR'ı `build/libs/` içine çıkar; `-sources.jar` dosyası moda yüklenmez.

## Lisans

ServerConfigSaver, MIT Lisansı ile yayımlanır. Tam metin için [LICENSE](LICENSE) dosyasına bak.
