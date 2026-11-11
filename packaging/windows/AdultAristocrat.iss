; Установщик Adult Aristocrat (Inno Setup 6)
; Сборка: ISCC.exe /DMyAppVersion=1.0.5 [/DWin7] AdultAristocrat.iss
; Без /DWin7 — установщик для Windows 10/11, с /DWin7 — для Windows 7 SP1 / 8.1.

#ifndef MyAppVersion
  #define MyAppVersion "1.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\..\composeApp\build\compose\binaries\main\app\AdultAristocrat"
#endif
#define MyAppName "Adult Aristocrat"
#ifdef Win7
  #define Suffix "win7"
#else
  #define Suffix "win10-11"
#endif
; Windows 7: запуск через javaw с отрисовкой OpenGL (кавычки внутри значения удваиваются)
#define Win7Exe "{app}\runtime\bin\javaw.exe"
#define Win7Base '-Dfile.encoding=UTF-8 -Xmx1024m ""-Dcompose.application.resources.dir={app}\app\resources"" -cp ""{app}\app\*"" com.adultaristocrat.cinema.MainKt'
#define Win7Args "-Dskiko.renderApi=OPENGL " + Win7Base
#define Win7ArgsSoft "-Dskiko.renderApi=SOFTWARE " + Win7Base

[Setup]
AppId={{8D3C6F0E-6B1A-4F7E-9D4C-2A7E5B1C9F30}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppName}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=..\..\out
OutputBaseFilename=AdultAristocrat-{#MyAppVersion}-{#Suffix}-setup
SetupIconFile=setup.ico
UninstallDisplayIcon={app}\AdultAristocrat.ico
UninstallDisplayName={#MyAppName}
WizardStyle=modern
WizardImageFile=wizard.bmp,wizard@2x.bmp
WizardSmallImageFile=wizard-small.bmp,wizard-small@2x.bmp
ShowLanguageDialog=yes
LanguageDetectionMethod=uilanguage
Compression=lzma2/max
SolidCompression=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequiredOverridesAllowed=dialog
CloseApplications=yes
#ifdef Win7
MinVersion=6.1sp1
#else
MinVersion=10.0
#endif

[Languages]
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "en"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
ru.LaunchApp=Запустить {#MyAppName}
en.LaunchApp=Launch {#MyAppName}
ru.SoftwareShortcut={#MyAppName} (программная отрисовка)
en.SoftwareShortcut={#MyAppName} (software rendering)

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "setup.ico"; DestDir: "{app}"; DestName: "AdultAristocrat.ico"; Flags: ignoreversion

[Icons]
#ifdef Win7
Name: "{group}\{#MyAppName}"; Filename: "{#Win7Exe}"; Parameters: "{#Win7Args}"; WorkingDir: "{app}"; IconFilename: "{app}\AdultAristocrat.ico"
Name: "{group}\{cm:SoftwareShortcut}"; Filename: "{#Win7Exe}"; Parameters: "{#Win7ArgsSoft}"; WorkingDir: "{app}"; IconFilename: "{app}\AdultAristocrat.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{#Win7Exe}"; Parameters: "{#Win7Args}"; WorkingDir: "{app}"; IconFilename: "{app}\AdultAristocrat.ico"; Tasks: desktopicon
#else
Name: "{group}\{#MyAppName}"; Filename: "{app}\AdultAristocrat.exe"; WorkingDir: "{app}"; IconFilename: "{app}\AdultAristocrat.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\AdultAristocrat.exe"; WorkingDir: "{app}"; IconFilename: "{app}\AdultAristocrat.ico"; Tasks: desktopicon
#endif
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"

[Run]
#ifdef Win7
Filename: "{#Win7Exe}"; Parameters: "{#Win7Args}"; WorkingDir: "{app}"; Description: "{cm:LaunchApp}"; Flags: nowait postinstall skipifsilent
#else
Filename: "{app}\AdultAristocrat.exe"; WorkingDir: "{app}"; Description: "{cm:LaunchApp}"; Flags: nowait postinstall skipifsilent
#endif
