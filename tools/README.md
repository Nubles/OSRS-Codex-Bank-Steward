# Rebuilding steward-data.json

`steward-data.json` lists every quest, miniquest and achievement diary tier with the items it needs. It is read from Quest Helper by running Quest Helper's own helpers, which is more accurate than parsing their source.

1. Clone RuneLite and Quest Helper, and compile both (RuneLite's `runelite-api` and `runelite-client` sources, then Quest Helper's sources against them).
2. Compile and run the extractor with Quest Helper, RuneLite and Mockito on the classpath. It mocks the client and Quest Helper's config, since helpers read them while loading their steps. Besides each helper's item requirements it reads the items named by its panels and steps, which is where quests like Cabin Fever list everything they use:

   ```bash
   javac -cp "$CP" -d build/tools tools/ExtractQuestHelper.java
   java -cp "build/tools:$CP" ExtractQuestHelper > build/quest-helper-items.json
   ```

3. Turn that into the plugin's data, with RuneLite's gameval `ItemID.java` and item names (`https://raw.githubusercontent.com/runelite/static.runelite.net/gh-pages/cache/item/names.json`):

   ```bash
   python3 tools/build_data.py build/quest-helper-items.json path/to/runelite-api/src/main/java/net/runelite/api/gameval/ItemID.java names.json
   ```

`build_data.py` also decides which items are general goods and which are lasting rewards; the rules and name patterns are at the top of the script.
