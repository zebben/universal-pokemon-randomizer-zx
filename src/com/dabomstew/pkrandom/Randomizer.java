package com.dabomstew.pkrandom;

/*----------------------------------------------------------------------------*/
/*--  Randomizer.java - Can randomize a file based on settings.             --*/
/*--                    Output varies by seed.                              --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

import com.dabomstew.pkrandom.pokemon.*;
import com.dabomstew.pkrandom.romhandlers.Gen1RomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

// Can randomize a file based on settings. Output varies by seed.
public class Randomizer {

    private static final String NEWLINE = System.getProperty("line.separator");

    private final Settings settings;
    private final RomHandler romHandler;
    private final ResourceBundle bundle;
    private final boolean saveAsDirectory;

    public Randomizer(Settings settings, RomHandler romHandler, ResourceBundle bundle, boolean saveAsDirectory) {
        this.settings = settings;
        this.romHandler = romHandler;
        this.bundle = bundle;
        this.saveAsDirectory = saveAsDirectory;
    }

    public int randomize(final String filename) {
        return randomize(filename, new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
            }
        }));
    }

    public int randomize(final String filename, final PrintStream log) {
        long seed = RandomSource.pickSeed();
        // long seed = 123456789;    // TESTING
        return randomize(filename, log, seed);
    }

    public int randomize(final String filename, final PrintStream log, long seed) {

        final long startTime = System.currentTimeMillis();
        RandomSource.seed(seed);

        int checkValue = 0;

        log.println("Randomizer Version: " + Version.VERSION_STRING);
        log.println("Random Seed: " + seed);
        log.println("Settings String: " + Version.VERSION + settings.toString());
        log.println();

        // set double battle mode
        romHandler.doubleBattleMode();

        // Record check value?
        romHandler.writeCheckValueToROM(checkValue);

        // Save
        if (saveAsDirectory) {
            romHandler.saveRomDirectory(filename);
        } else {
            romHandler.saveRomFile(filename, seed);
        }

        // Log tail
        String gameName = romHandler.getROMName();
        if (romHandler.hasGameUpdateLoaded()) {
            gameName = gameName + " (" + romHandler.getGameUpdateVersion() + ")";
        }
        log.println("------------------------------------------------------------------");
        log.println("Randomization of " + gameName + " completed.");
        log.println("Time elapsed: " + (System.currentTimeMillis() - startTime) + "ms");
        log.println("RNG Calls: " + RandomSource.callsSinceSeed());
        log.println("------------------------------------------------------------------");
        log.println();

        // Diagnostics
        log.println("--ROM Diagnostics--");
        if (!romHandler.isRomValid()) {
            log.println(bundle.getString("Log.InvalidRomLoaded"));
        }
        romHandler.printRomDiagnostics(log);

        return checkValue;
    }

    private int logMoveTutorMoves(PrintStream log, int checkValue, List<Integer> oldMtMoves) {
        log.println("--Move Tutor Moves--");
        List<Integer> newMtMoves = romHandler.getMoveTutorMoves();
        List<Move> moves = romHandler.getMoves();
        for (int i = 0; i < newMtMoves.size(); i++) {
            log.printf("%-10s -> %-10s" + NEWLINE, moves.get(oldMtMoves.get(i)).name,
                    moves.get(newMtMoves.get(i)).name);
            checkValue = addToCV(checkValue, newMtMoves.get(i));
        }
        log.println();
        return checkValue;
    }

    private int logTMMoves(PrintStream log, int checkValue) {
        log.println("--TM Moves--");
        List<Integer> tmMoves = romHandler.getTMMoves();
        List<Move> moves = romHandler.getMoves();
        for (int i = 0; i < tmMoves.size(); i++) {
            log.printf("TM%02d %s" + NEWLINE, i + 1, moves.get(tmMoves.get(i)).name);
            checkValue = addToCV(checkValue, tmMoves.get(i));
        }
        log.println();
        return checkValue;
    }

    private void logTrades(PrintStream log, List<IngameTrade> oldTrades) {
        log.println("--In-Game Trades--");
        List<IngameTrade> newTrades = romHandler.getIngameTrades();
        int size = oldTrades.size();
        for (int i = 0; i < size; i++) {
            IngameTrade oldT = oldTrades.get(i);
            IngameTrade newT = newTrades.get(i);
            log.printf("Trade %-11s -> %-11s the %-11s        ->      %-11s -> %-15s the %s" + NEWLINE,
                    oldT.requestedPokemon != null ? oldT.requestedPokemon.fullName() : "Any",
                    oldT.nickname, oldT.givenPokemon.fullName(),
                    newT.requestedPokemon != null ? newT.requestedPokemon.fullName() : "Any",
                    newT.nickname, newT.givenPokemon.fullName());
        }
        log.println();
    }

    private void logMovesetChanges(PrintStream log) {
        log.println("--Pokemon Movesets--");
        List<String> movesets = new ArrayList<>();
        Map<Integer, List<MoveLearnt>> moveData = romHandler.getMovesLearnt();
        Map<Integer, List<Integer>> eggMoves = romHandler.getEggMoves();
        List<Move> moves = romHandler.getMoves();
        List<Pokemon> pkmnList = romHandler.getPokemonInclFormes();
        int i = 1;
        for (Pokemon pkmn : pkmnList) {
            if (pkmn == null || pkmn.actuallyCosmetic) {
                continue;
            }
            StringBuilder evoStr = new StringBuilder();
            try {
                evoStr.append(" -> ").append(pkmn.evolutionsFrom.get(0).to.fullName());
            } catch (Exception e) {
                evoStr.append(" (no evolution)");
            }

            StringBuilder sb = new StringBuilder();

            if (romHandler instanceof Gen1RomHandler) {
                sb.append(String.format("%03d %s", i, pkmn.fullName()))
                        .append(evoStr).append(System.getProperty("line.separator"))
                        .append(String.format("HP   %-3d", pkmn.hp)).append(System.getProperty("line.separator"))
                        .append(String.format("ATK  %-3d", pkmn.attack)).append(System.getProperty("line.separator"))
                        .append(String.format("DEF  %-3d", pkmn.defense)).append(System.getProperty("line.separator"))
                        .append(String.format("SPEC %-3d", pkmn.special)).append(System.getProperty("line.separator"))
                        .append(String.format("SPE  %-3d", pkmn.speed)).append(System.getProperty("line.separator"));
            } else {
                sb.append(String.format("%03d %s", i, pkmn.fullName()))
                        .append(evoStr).append(System.getProperty("line.separator"))
                        .append(String.format("HP  %-3d", pkmn.hp)).append(System.getProperty("line.separator"))
                        .append(String.format("ATK %-3d", pkmn.attack)).append(System.getProperty("line.separator"))
                        .append(String.format("DEF %-3d", pkmn.defense)).append(System.getProperty("line.separator"))
                        .append(String.format("SPA %-3d", pkmn.spatk)).append(System.getProperty("line.separator"))
                        .append(String.format("SPD %-3d", pkmn.spdef)).append(System.getProperty("line.separator"))
                        .append(String.format("SPE %-3d", pkmn.speed)).append(System.getProperty("line.separator"));
            }

            i++;

            List<MoveLearnt> data = moveData.get(pkmn.number);
            for (MoveLearnt ml : data) {
                try {
                    if (ml.level == 0) {
                        sb.append("Learned upon evolution: ")
                                .append(moves.get(ml.move).name).append(System.getProperty("line.separator"));
                    } else {
                        sb.append("Level ")
                                .append(String.format("%-2d", ml.level))
                                .append(": ")
                                .append(moves.get(ml.move).name).append(System.getProperty("line.separator"));
                    }
                } catch (NullPointerException ex) {
                    sb.append("invalid move at level").append(ml.level);
                }
            }
            List<Integer> eggMove = eggMoves.get(pkmn.number);
            if (eggMove != null && eggMove.size() != 0) {
                sb.append("Egg Moves:").append(System.getProperty("line.separator"));
                for (Integer move : eggMove) {
                    sb.append(" - ").append(moves.get(move).name).append(System.getProperty("line.separator"));
                }
            }

            movesets.add(sb.toString());
        }
        Collections.sort(movesets);
        for (String moveset : movesets) {
            log.println(moveset);
        }
        log.println();
    }

    private void logMoveUpdates(PrintStream log) {
        log.println("--Move Updates--");
        List<Move> moves = romHandler.getMoves();
        Map<Integer, boolean[]> moveUpdates = romHandler.getMoveUpdates();
        for (int moveID : moveUpdates.keySet()) {
            boolean[] changes = moveUpdates.get(moveID);
            Move mv = moves.get(moveID);
            List<String> nonTypeChanges = new ArrayList<>();
            if (changes[0]) {
                nonTypeChanges.add(String.format("%d power", mv.power));
            }
            if (changes[1]) {
                nonTypeChanges.add(String.format("%d PP", mv.pp));
            }
            if (changes[2]) {
                nonTypeChanges.add(String.format("%.00f%% accuracy", mv.hitratio));
            }
            String logStr = "Made " + mv.name;
            // type or not?
            if (changes[3]) {
                logStr += " be " + mv.type + "-type";
                if (nonTypeChanges.size() > 0) {
                    logStr += " and";
                }
            }
            if (changes[4]) {
                if (mv.category == MoveCategory.PHYSICAL) {
                    logStr += " a Physical move";
                } else if (mv.category == MoveCategory.SPECIAL) {
                    logStr += " a Special move";
                } else if (mv.category == MoveCategory.STATUS) {
                    logStr += " a Status move";
                }
            }
            if (nonTypeChanges.size() > 0) {
                logStr += " have ";
                if (nonTypeChanges.size() == 3) {
                    logStr += nonTypeChanges.get(0) + ", " + nonTypeChanges.get(1) + " and " + nonTypeChanges.get(2);
                } else if (nonTypeChanges.size() == 2) {
                    logStr += nonTypeChanges.get(0) + " and " + nonTypeChanges.get(1);
                } else {
                    logStr += nonTypeChanges.get(0);
                }
            }
            log.println(logStr);
        }
        log.println();
    }

    private void logEvolutionChanges(PrintStream log) {
        log.println("--Randomized Evolutions--");
        List<Pokemon> allPokes = romHandler.getPokemonInclFormes();
        for (Pokemon pk : allPokes) {
            if (pk != null && !pk.actuallyCosmetic) {
                int numEvos = pk.evolutionsFrom.size();
                if (numEvos > 0) {
                    StringBuilder evoStr = new StringBuilder(pk.evolutionsFrom.get(0).toFullName());
                    for (int i = 1; i < numEvos; i++) {
                        if (i == numEvos - 1) {
                            evoStr.append(" and ").append(pk.evolutionsFrom.get(i).toFullName());
                        } else {
                            evoStr.append(", ").append(pk.evolutionsFrom.get(i).toFullName());
                        }
                    }
                    log.printf("%-15s -> %-15s" + NEWLINE, pk.fullName(), evoStr.toString());
                }
            }
        }

        log.println();
    }

    private void logPokemonTraitChanges(final PrintStream log) {
        List<Pokemon> allPokes = romHandler.getPokemonInclFormes();
        String[] itemNames = romHandler.getItemNames();
        // Log base stats & types
        log.println("--Pokemon Base Stats & Types--");
        if (romHandler instanceof Gen1RomHandler) {
            log.println("NUM|NAME      |TYPE             |  HP| ATK| DEF| SPE|SPEC");
            for (Pokemon pkmn : allPokes) {
                if (pkmn != null) {
                    String typeString = pkmn.primaryType == null ? "???" : pkmn.primaryType.toString();
                    if (pkmn.secondaryType != null) {
                        typeString += "/" + pkmn.secondaryType.toString();
                    }
                    log.printf("%3d|%-10s|%-17s|%4d|%4d|%4d|%4d|%4d" + NEWLINE, pkmn.number, pkmn.fullName(), typeString,
                            pkmn.hp, pkmn.attack, pkmn.defense, pkmn.speed, pkmn.special );
                }

            }
        } else {
            String nameSp = "      ";
            String nameSpFormat = "%-13s";
            String abSp = "    ";
            String abSpFormat = "%-12s";
            if (romHandler.generationOfPokemon() == 5) {
                nameSp = "         ";
            } else if (romHandler.generationOfPokemon() == 6) {
                nameSp = "            ";
                nameSpFormat = "%-16s";
                abSp = "      ";
                abSpFormat = "%-14s";
            } else if (romHandler.generationOfPokemon() >= 7) {
                nameSp = "            ";
                nameSpFormat = "%-16s";
                abSp = "        ";
                abSpFormat = "%-16s";
            }

            log.print("NUM|NAME" + nameSp + "|TYPE             |  HP| ATK| DEF|SATK|SDEF| SPD");
            int abils = romHandler.abilitiesPerPokemon();
            for (int i = 0; i < abils; i++) {
                log.print("|ABILITY" + (i + 1) + abSp);
            }
            log.print("|ITEM");
            log.println();
            int i = 0;
            for (Pokemon pkmn : allPokes) {
                if (pkmn != null && !pkmn.actuallyCosmetic) {
                    i++;
                    String typeString = pkmn.primaryType == null ? "???" : pkmn.primaryType.toString();
                    if (pkmn.secondaryType != null) {
                        typeString += "/" + pkmn.secondaryType.toString();
                    }
                    log.printf("%3d|" + nameSpFormat + "|%-17s|%4d|%4d|%4d|%4d|%4d|%4d", i, pkmn.fullName(), typeString,
                            pkmn.hp, pkmn.attack, pkmn.defense, pkmn.spatk, pkmn.spdef, pkmn.speed);
                    if (abils > 0) {
                        log.printf("|" + abSpFormat + "|" + abSpFormat, romHandler.abilityName(pkmn.ability1),
                                pkmn.ability1 == pkmn.ability2 ? "--" : romHandler.abilityName(pkmn.ability2));
                        if (abils > 2) {
                            log.printf("|" + abSpFormat, romHandler.abilityName(pkmn.ability3));
                        }
                    }
                    log.print("|");
                    if (pkmn.guaranteedHeldItem > 0) {
                        log.print(itemNames[pkmn.guaranteedHeldItem] + " (100%)");
                    } else {
                        int itemCount = 0;
                        if (pkmn.commonHeldItem > 0) {
                            itemCount++;
                            log.print(itemNames[pkmn.commonHeldItem] + " (common)");
                        }
                        if (pkmn.rareHeldItem > 0) {
                            if (itemCount > 0) {
                                log.print(", ");
                            }
                            itemCount++;
                            log.print(itemNames[pkmn.rareHeldItem] + " (rare)");
                        }
                        if (pkmn.darkGrassHeldItem > 0) {
                            if (itemCount > 0) {
                                log.print(", ");
                            }
                            log.print(itemNames[pkmn.darkGrassHeldItem] + " (dark grass only)");
                        }
                    }
                    log.println();
                }

            }
        }
        log.println();
    }

    private void logTMHMCompatibility(final PrintStream log) {
        log.println("--TM Compatibility--");
        Map<Pokemon, boolean[]> compat = romHandler.getTMHMCompatibility();
        List<Integer> tmHMs = new ArrayList<>(romHandler.getTMMoves());
        tmHMs.addAll(romHandler.getHMMoves());
        List<Move> moveData = romHandler.getMoves();

        logCompatibility(log, compat, tmHMs, moveData, true);
    }

    private void logTutorCompatibility(final PrintStream log) {
        log.println("--Move Tutor Compatibility--");
        Map<Pokemon, boolean[]> compat = romHandler.getMoveTutorCompatibility();
        List<Integer> tutorMoves = romHandler.getMoveTutorMoves();
        List<Move> moveData = romHandler.getMoves();

        logCompatibility(log, compat, tutorMoves, moveData, false);
    }

    private void logCompatibility(final PrintStream log, Map<Pokemon, boolean[]> compat, List<Integer> moveList,
                                  List<Move> moveData, boolean includeTMNumber) {
        int tmCount = romHandler.getTMCount();
        for (Map.Entry<Pokemon, boolean[]> entry : compat.entrySet()) {
            Pokemon pkmn = entry.getKey();
            if (pkmn.actuallyCosmetic) continue;
            boolean[] flags = entry.getValue();

            String nameSpFormat = "%-14s";
            if (romHandler.generationOfPokemon() >= 6) {
                nameSpFormat = "%-17s";
            }
            log.printf("%3d " + nameSpFormat, pkmn.number, pkmn.fullName() + " ");

            for (int i = 1; i < flags.length; i++) {
                String moveName = moveData.get(moveList.get(i - 1)).name;
                if (moveName.length() == 0) {
                    moveName = "(BLANK)";
                }
                int moveNameLength = moveName.length();
                if (flags[i]) {
                    if (includeTMNumber) {
                        if (i <= tmCount) {
                            log.printf("|TM%02d %" + moveNameLength + "s ", i, moveName);
                        } else {
                            log.printf("|HM%02d %" + moveNameLength + "s ", i-tmCount, moveName);
                        }
                    } else {
                        log.printf("|%" + moveNameLength + "s ", moveName);
                    }
                } else {
                    if (includeTMNumber) {
                        log.printf("| %" + (moveNameLength+4) + "s ", "-");
                    } else {
                        log.printf("| %" + (moveNameLength-1) + "s ", "-");
                    }
                }
            }
            log.println("|");
        }
        log.println("");
    }

    private void logUpdatedEvolutions(final PrintStream log, Set<EvolutionUpdate> updatedEvolutions,
                                      Set<EvolutionUpdate> otherUpdatedEvolutions) {
        for (EvolutionUpdate evo: updatedEvolutions) {
            if (otherUpdatedEvolutions != null && otherUpdatedEvolutions.contains(evo)) {
                log.println(evo.toString() + " (Overwritten by \"Make Evolutions Easier\", see below)");
            } else {
                log.println(evo.toString());
            }
        }
        log.println();
    }

    private void logStarters(final PrintStream log) {

        switch(settings.getStartersMod()) {
            case CUSTOM:
                log.println("--Custom Starters--");
                break;
            case COMPLETELY_RANDOM:
                log.println("--Random Starters--");
                break;
            case RANDOM_WITH_TWO_EVOLUTIONS:
                log.println("--Random 2-Evolution Starters--");
                break;
            default:
                break;
        }

        List<Pokemon> starters = romHandler.getPickedStarters();
        int i = 1;
        for (Pokemon starter: starters) {
            log.println("Set starter " + i + " to " + starter.fullName());
            i++;
        }
        log.println();
    }

    private void logWildPokemonChanges(final PrintStream log) {

        log.println("--Wild Pokemon--");
        boolean useTimeBasedEncounters = settings.isUseTimeBasedEncounters() ||
                (settings.getWildPokemonMod() == Settings.WildPokemonMod.UNCHANGED && settings.isWildLevelsModified());
        List<EncounterSet> encounters = romHandler.getEncounters(useTimeBasedEncounters);
        int idx = 0;
        for (EncounterSet es : encounters) {
            idx++;
            log.print("Set #" + idx + " ");
            if (es.displayName != null) {
                log.print("- " + es.displayName + " ");
            }
            log.print("(rate=" + es.rate + ")");
            log.println();
            for (Encounter e : es.encounters) {
                StringBuilder sb = new StringBuilder();
                if (e.isSOS) {
                    String stringToAppend;
                    switch (e.sosType) {
                        case RAIN:
                            stringToAppend = "Rain SOS: ";
                            break;
                        case HAIL:
                            stringToAppend = "Hail SOS: ";
                            break;
                        case SAND:
                            stringToAppend = "Sand SOS: ";
                            break;
                        default:
                            stringToAppend = "  SOS: ";
                            break;
                    }
                    sb.append(stringToAppend);
                }
                sb.append(e.pokemon.fullName()).append(" Lv");
                if (e.maxLevel > 0 && e.maxLevel != e.level) {
                    sb.append("s ").append(e.level).append("-").append(e.maxLevel);
                } else {
                    sb.append(e.level);
                }
                String whitespaceFormat = romHandler.generationOfPokemon() == 7 ? "%-31s" : "%-25s";
                log.print(String.format(whitespaceFormat, sb));
                StringBuilder sb2 = new StringBuilder();
                if (romHandler instanceof Gen1RomHandler) {
                    sb2.append(String.format("HP %-3d ATK %-3d DEF %-3d SPECIAL %-3d SPEED %-3d", e.pokemon.hp, e.pokemon.attack, e.pokemon.defense, e.pokemon.special, e.pokemon.speed));
                } else {
                    sb2.append(String.format("HP %-3d ATK %-3d DEF %-3d SPATK %-3d SPDEF %-3d SPEED %-3d", e.pokemon.hp, e.pokemon.attack, e.pokemon.defense, e.pokemon.spatk, e.pokemon.spdef, e.pokemon.speed));
                }
                log.print(sb2);
                log.println();
            }
            log.println();
        }
        log.println();
    }

    private void maybeLogTrainerChanges(final PrintStream log, List<String> originalTrainerNames, boolean trainerNamesChanged, boolean logTrainerMovesets) {
        log.println("--Trainers Pokemon--");
        List<Trainer> trainers = romHandler.getTrainers();
        for (Trainer t : trainers) {
            log.print("#" + t.index + " ");
            String originalTrainerName = originalTrainerNames.get(t.index);
            String currentTrainerName = "";
            if (t.fullDisplayName != null) {
                currentTrainerName = t.fullDisplayName;
            } else if (t.name != null) {
                currentTrainerName = t.name;
            }
            if (!currentTrainerName.isEmpty()) {
                if (trainerNamesChanged) {
                    log.printf("(%s => %s)", originalTrainerName, currentTrainerName);
                } else {
                    log.printf("(%s)", currentTrainerName);
                }
            }
            if (t.offset != 0) {
                log.printf("@%X", t.offset);
            }

            String[] itemNames = romHandler.getItemNames();
            if (logTrainerMovesets) {
                log.println();
                for (TrainerPokemon tpk : t.pokemon) {
                    List<Move> moves = romHandler.getMoves();
                    log.printf(tpk.toString(), itemNames[tpk.heldItem]);
                    log.print(", Ability: " + romHandler.abilityName(romHandler.getAbilityForTrainerPokemon(tpk)));
                    log.print(" - ");
                    boolean first = true;
                    for (int move : tpk.moves) {
                        if (move != 0) {
                            if (!first) {
                                log.print(", ");
                            }
                            log.print(moves.get(move).name);
                            first = false;
                        }
                    }
                    log.println();
                }
            } else {
                log.print(" - ");
                boolean first = true;
                for (TrainerPokemon tpk : t.pokemon) {
                    if (!first) {
                        log.print(", ");
                    }
                    log.printf(tpk.toString(), itemNames[tpk.heldItem]);
                    first = false;
                }
            }
            log.println();
        }
        log.println();
    }

    private int logStaticPokemon(final PrintStream log, int checkValue, List<StaticEncounter> oldStatics) {

        List<StaticEncounter> newStatics = romHandler.getStaticPokemon();

        log.println("--Static Pokemon--");
        Map<String, Integer> seenPokemon = new TreeMap<>();
        for (int i = 0; i < oldStatics.size(); i++) {
            StaticEncounter oldP = oldStatics.get(i);
            StaticEncounter newP = newStatics.get(i);
            checkValue = addToCV(checkValue, newP.pkmn.number);
            String oldStaticString = oldP.toString(settings.isStaticLevelModified());
            log.print(oldStaticString);
            if (seenPokemon.containsKey(oldStaticString)) {
                int amount = seenPokemon.get(oldStaticString);
                log.print("(" + (++amount) + ")");
                seenPokemon.put(oldStaticString, amount);
            } else {
                seenPokemon.put(oldStaticString, 1);
            }
            log.println(" => " + newP.toString(settings.isStaticLevelModified()));
        }
        log.println();

        return checkValue;
    }

    private int logTotemPokemon(final PrintStream log, int checkValue, List<TotemPokemon> oldTotems) {

        List<TotemPokemon> newTotems = romHandler.getTotemPokemon();

        String[] itemNames = romHandler.getItemNames();
        log.println("--Totem Pokemon--");
        for (int i = 0; i < oldTotems.size(); i++) {
            TotemPokemon oldP = oldTotems.get(i);
            TotemPokemon newP = newTotems.get(i);
            checkValue = addToCV(checkValue, newP.pkmn.number);
            log.println(oldP.pkmn.fullName() + " =>");
            log.printf(newP.toString(),itemNames[newP.heldItem]);
        }
        log.println();

        return checkValue;
    }

    private void logMoveChanges(final PrintStream log) {

        log.println("--Move Data--");
        log.print("NUM|NAME           |TYPE    |POWER|ACC.|PP");
        if (romHandler.hasPhysicalSpecialSplit()) {
            log.print(" |CATEGORY");
        }
        log.println();
        List<Move> allMoves = romHandler.getMoves();
        for (Move mv : allMoves) {
            if (mv != null) {
                String mvType = (mv.type == null) ? "???" : mv.type.toString();
                log.printf("%3d|%-15s|%-8s|%5d|%4d|%3d", mv.internalId, mv.name, mvType, mv.power,
                        (int) mv.hitratio, mv.pp);
                if (romHandler.hasPhysicalSpecialSplit()) {
                    log.printf("| %s", mv.category.toString());
                }
                log.println();
            }
        }
        log.println();
    }

    private void logShops(final PrintStream log) {
        String[] itemNames = romHandler.getItemNames();
        log.println("--Shops--");
        Map<Integer, Shop> shopsDict = romHandler.getShopItems();
        for (int shopID : shopsDict.keySet()) {
            Shop shop = shopsDict.get(shopID);
            log.printf("%s", shop.name);
            log.println();
            List<Integer> shopItems = shop.items;
            for (int shopItemID : shopItems) {
                log.printf("- %5s", itemNames[shopItemID]);
                log.println();
            }
            
            log.println();
        }
        log.println();
    }

    private void logPickupItems(final PrintStream log) {
        List<PickupItem> pickupItems = romHandler.getPickupItems();
        String[] itemNames = romHandler.getItemNames();
        log.println("--Pickup Items--");
        for (int levelRange = 0; levelRange < 10; levelRange++) {
            int startingLevel = (levelRange * 10) + 1;
            int endingLevel = (levelRange + 1) * 10;
            log.printf("Level %s-%s", startingLevel, endingLevel);
            log.println();
            TreeMap<Integer, List<String>> itemListPerProbability = new TreeMap<>();
            for (PickupItem pickupItem : pickupItems) {
                int probability = pickupItem.probabilities[levelRange];
                if (itemListPerProbability.containsKey(probability)) {
                    itemListPerProbability.get(probability).add(itemNames[pickupItem.item]);
                } else if (probability > 0) {
                    List<String> itemList = new ArrayList<>();
                    itemList.add(itemNames[pickupItem.item]);
                    itemListPerProbability.put(probability, itemList);
                }
            }
            for (Map.Entry<Integer, List<String>> itemListPerProbabilityEntry : itemListPerProbability.descendingMap().entrySet()) {
                int probability = itemListPerProbabilityEntry.getKey();
                List<String> itemList = itemListPerProbabilityEntry.getValue();
                String itemsString = String.join(", ", itemList);
                log.printf("%d%%: %s", probability, itemsString);
                log.println();
            }
            log.println();
        }
        log.println();
    }

    private List<String> getTrainerNames() {
        List<String> trainerNames = new ArrayList<>();
        trainerNames.add(""); // for index 0
        List<Trainer> trainers = romHandler.getTrainers();
        for (Trainer t : trainers) {
            if (t.fullDisplayName != null) {
                trainerNames.add(t.fullDisplayName);
            } else if (t.name != null) {
                trainerNames.add(t.name);
            } else {
                trainerNames.add("");
            }
        }
        return trainerNames;
    }

    
    private static int addToCV(int checkValue, int... values) {
        for (int value : values) {
            checkValue = Integer.rotateLeft(checkValue, 3);
            checkValue ^= value;
        }
        return checkValue;
    }
}