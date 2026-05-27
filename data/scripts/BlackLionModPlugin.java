package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

public class BlackLionModPlugin extends BaseModPlugin {

    public static final String SPAWNED_KEY = "$blackLion_spawned";
    private static final Logger log = Global.getLogger(BlackLionModPlugin.class);

    private static final String[] SMODS = {
            "auxiliarythrusters",
            "hardenedshieldemitter",
            "reinforcedhull",
    };

    @Override
    public void onNewGameAfterProcGen() {
        log.info("BlackLion: onNewGameAfterProcGen fired");
        spawnBlackLionFleet();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        log.info("BlackLion: onGameLoad fired, newGame=" + newGame);
        if (!Global.getSector().getMemoryWithoutUpdate().contains(SPAWNED_KEY)) {
            log.info("BlackLion: not yet spawned, attempting spawn on existing save");
            spawnBlackLionFleet();
        } else {
            log.info("BlackLion: already spawned");
        }
    }

    private void spawnBlackLionFleet() {
        try {
            doSpawn();
        } catch (Exception e) {
            log.error("BlackLion: failed to spawn fleet", e);
        }
    }

    private void doSpawn() {
        SectorAPI sector = Global.getSector();

        StarSystemAPI askonia = null;
        for (StarSystemAPI sys : sector.getStarSystems()) {
            if ("Askonia".equals(sys.getBaseName())) {
                askonia = sys;
                break;
            }
        }
        if (askonia == null) {
            log.error("BlackLion: could not find Askonia system");
            return;
        }
        log.info("BlackLion: found Askonia system");

        SectorEntityToken patrolTarget = findFringeJumpPoint(askonia);
        if (patrolTarget == null) {
            log.warn("BlackLion: no fringe jump point, falling back to star");
            patrolTarget = askonia.getStar();
        }
        log.info("BlackLion: patrol target = " + patrolTarget.getName());

        FactionAPI faction = sector.getFaction("sindrian_diktat");

        Random random = new Random();

        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet("sindrian_diktat", "The Black Lion", true);
        fleet.setFaction("sindrian_diktat", true);

        // Fleet commander (level 8, cautious)
        PersonAPI commander = OfficerManagerEvent.createOfficer(
                faction, 8,
                OfficerManagerEvent.SkillPickPreference.ANY,
                false, null, true, true, 1, random);
        commander.setRankId(Ranks.SPACE_ADMIRAL);
        commander.setPostId(Ranks.POST_FLEET_COMMANDER);
        commander.setPersonality(Personalities.CAUTIOUS);
        fleet.setCommander(commander);

        // Flagship: The Black Lion (30 DP)
        FleetMemberAPI flagship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "executor_2_Elite");
        fleet.getFleetData().addFleetMember(flagship);
        fleet.getFleetData().ensureHasFlagship();
        fleet.getFlagship().setCaptain(commander);
        fleet.getFlagship().setVariant(cloneVariant(fleet.getFlagship().getVariant()), false, false);
        fleet.getFlagship().getVariant().setSource(VariantSource.REFIT);
        fleet.getFlagship().getVariant().addTag(Tags.VARIANT_ALWAYS_RECOVERABLE);

        // Black Lion Eagles (2x14 = 28 DP)
        addShip(fleet, "eagle_LG_2_Assault");
        addShip(fleet, "eagle_LG_2_Assault");

        // Phase transports (3x5 = 15 DP)
        addShip(fleet, "revenant_Elite");
        addShip(fleet, "revenant_Elite");
        addShip(fleet, "revenant_Elite");

        // Regular escorts with no special modifications (167 DP)
        addShip(fleet, "eagle_Assault");
        addShip(fleet, "falcon_Attack");
        addShip(fleet, "falcon_Attack");
        addShip(fleet, "hammerhead_Balanced");
        addShip(fleet, "hammerhead_Balanced");
        addShip(fleet, "hammerhead_Balanced");
        addShip(fleet, "sunder_Assault");
        addShip(fleet, "sunder_Assault");
        addShip(fleet, "centurion_Assault");
        addShip(fleet, "centurion_Assault");
        addShip(fleet, "centurion_Assault");
        addShip(fleet, "centurion_Assault");
        addShip(fleet, "centurion_Assault");
        addShip(fleet, "brawler_lg_2_Elite");
        addShip(fleet, "brawler_lg_2_Elite");
        addShip(fleet, "brawler_lg_2_Elite");
        addShip(fleet, "brawler_lg_2_Elite");
        addShip(fleet, "brawler_lg_2_Elite");
        addShip(fleet, "brawler_lg_2_Elite");
        addShip(fleet, "brawler_lg_2_Elite");

        log.info("BlackLion: fleet has " + fleet.getFleetData().getMembersListCopy().size() + " ships");

        // Add 3 built-in s-mods to all non-Black Lion ships
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            String hullId = member.getHullId();
            if ("executor_2".equals(hullId) || "eagle_LG_2".equals(hullId) || "brawler_lg_2".equals(hullId)) continue;

            member.setVariant(cloneVariant(member.getVariant()), false, false);
            member.getVariant().setSource(VariantSource.REFIT);
            for (String smod : SMODS) {
                member.getVariant().addPermaMod(smod, true);
            }
            member.getVariant().addTag(Tags.VARIANT_ALWAYS_RETAIN_SMODS_ON_SALVAGE);
        }

        // Assign officers to every ship without one
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getCaptain() != null && !member.getCaptain().isDefault()) continue;

            if ("brawler_lg_2".equals(member.getHullId())) {
                continue;
            }

            int level = 3 + random.nextInt(3);
            PersonAPI officer = OfficerManagerEvent.createOfficer(
                    faction, level,
                    OfficerManagerEvent.SkillPickPreference.ANY,
                    false, null, true, true, 1, random);
            officer.setPersonality(Personalities.CAUTIOUS);
            member.setCaptain(officer);
            fleet.getFleetData().addOfficer(officer);
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
        fleet.setTransponderOn(true);

        Misc.makeImportant(fleet, "black_lion");

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        }

        fleet.getFleetData().sort();
        fleet.forceSync();

        askonia.addEntity(fleet);
        fleet.setLocation(patrolTarget.getLocation().x + 500f, patrolTarget.getLocation().y);
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, patrolTarget, 999999f);

        sector.getMemoryWithoutUpdate().set(SPAWNED_KEY, true);
        log.info("BlackLion: fleet spawned successfully near " + patrolTarget.getName());
    }

    private SectorEntityToken findFringeJumpPoint(StarSystemAPI system) {
        float maxDist = 0f;
        SectorEntityToken fringe = null;
        List<JumpPointAPI> jumpPoints = system.getEntities(JumpPointAPI.class);
        log.info("BlackLion: found " + jumpPoints.size() + " jump points in system");
        for (JumpPointAPI jp : jumpPoints) {
            float dist = jp.getCircularOrbitRadius();
            log.info("BlackLion: jump point '" + jp.getName() + "' at radius " + dist);
            if (dist > maxDist) {
                maxDist = dist;
                fringe = jp;
            }
        }
        return fringe;
    }

    private ShipVariantAPI cloneVariant(ShipVariantAPI variant) {
        try {
            return (ShipVariantAPI) variant.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    private void addShip(CampaignFleetAPI fleet, String variantId) {
        fleet.getFleetData().addFleetMember(
                Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId));
    }
}
