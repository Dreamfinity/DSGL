[working-directory("msdf-atlas-gen")]
build-msdf-atlas-gen:
    cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DMSDF_ATLAS_USE_SKIA=OFF
    cmake --build build --target msdf-atlas-gen-standalone

build-full-atlas:
    ./bin/msdf-atlas-gen.exe \
        -font './fonts/noto/Noto_Sans/NotoSans-VariableFont.ttf' -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Mono/NotoSansMono-VariableFont.ttf" -allglyphs \
        -and -font './fonts/noto/Noto_Sans_Arabic/NotoSansArabic-VariableFont.ttf' -allglyphs \
        -and -font './fonts/noto/Noto_Sans_Armenian/NotoSansArmenian-VariableFont.ttf' -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Georgian/NotoSansGeorgian-VariableFont.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Hebrew/NotoSansHebrew-VariableFont.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Kannada/NotoSansKannada-VariableFont.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Lao/NotoSansLao-VariableFont.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Malayalam/NotoSansMalayalam-VariableFont.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Old_Hungarian/NotoSansOldHungarian-Regular.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Old_Persian/NotoSansOldPersian-Regular.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_JP/NotoSansJP-VariableFont_wght.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_KR/NotoSansKR-VariableFont_wght.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_SC/NotoSansSC-VariableFont_wght.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_TC/NotoSansTC-VariableFont_wght.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Symbols/NotoSansSymbols-VariableFont_wght.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Symbols_2/NotoSansSymbols2-Regular.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Tamil/NotoSansTamil-VariableFont.ttf" -allglyphs \
        -and -font "./fonts/noto/Noto_Sans_Thai/NotoSansThai-VariableFont.ttf" -allglyphs \
        -imageout "noto.png" -json "noto.json" -type mtsdf -threads 16
