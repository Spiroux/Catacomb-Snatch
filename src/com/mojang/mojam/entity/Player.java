package com.mojang.mojam.entity;

import java.util.Random;

import com.mojang.mojam.GameCharacter;
import com.mojang.mojam.Keys;
import com.mojang.mojam.MojamComponent;
import com.mojang.mojam.MouseButtons;
import com.mojang.mojam.Options;
import com.mojang.mojam.entity.animation.SmokePuffAnimation;
import com.mojang.mojam.entity.building.Building;
import com.mojang.mojam.entity.building.Harvester;
import com.mojang.mojam.entity.building.ShopItem;
import com.mojang.mojam.entity.building.Turret;
import com.mojang.mojam.entity.loot.Loot;
import com.mojang.mojam.entity.loot.LootCollector;
import com.mojang.mojam.entity.mob.Mob;
import com.mojang.mojam.entity.mob.RailDroid;
import com.mojang.mojam.entity.mob.Team;
import com.mojang.mojam.entity.particle.Sparkle;
import com.mojang.mojam.entity.weapon.IWeapon;
import com.mojang.mojam.entity.weapon.Rifle;
import com.mojang.mojam.gui.Notifications;
import com.mojang.mojam.level.tile.RailTile;
import com.mojang.mojam.level.tile.Tile;
import com.mojang.mojam.math.Vec2;
import com.mojang.mojam.network.TurnSynchronizer;
import com.mojang.mojam.screen.Art;
import com.mojang.mojam.screen.Bitmap;
import com.mojang.mojam.screen.Screen;

/**
 * Implements the player entity
 */
public class Player extends Mob implements LootCollector {
    public static int COST_RAIL;
    public static int COST_DROID;
    public static int COST_REMOVE_RAIL;
    public int REGEN_INTERVAL = 60 * 3;
    public int plevel;
    public double pexp;
    public double psprint;
    public boolean isSprint = false;
    public int timeSprint = 0;
    public int maxTimeSprint;
    public Keys keys;
    public MouseButtons mouseButtons;
    public int mouseFireButton = 1;
    public int mouseUseButton = 3;

    
    private boolean mouseAiming;
   
    public int takeDelay = 0;
    public int flashTime = 0;
    public int suckRadius = 0;
    public boolean wasShooting;
    public int score = 0;
    private int facing = 0;
    private int time = 0;
    private int walkTime = 0;
    private Building selected = null;
    static final int RailDelayTicks = 15;
    private int lastRailTick = -999;
    private final static int INTERACT_DISTANCE = 20 * 20; // Sqr
    private int steps = 0;
    private boolean isSeeing;
    private int startX;
    private int startY;
    public int muzzleTicks = 0;
    public double muzzleX = 0;
    public double muzzleY = 0;
    private int muzzleImage = 0;
    private boolean dead = false;
    private int deadDelay = 0;
    private int nextWalkSmokeTick = 0;
    boolean isImmortal;
    private GameCharacter character;

    /**
     * Constructor
     * 
     * @param keys Key bindings for this player
     * @param mouseButtons Mouse Button state
     * @param x Initial x coordinate
     * @param y Initial y coordinate
     * @param team Team number
     */
    public Player(Keys keys, MouseButtons mouseButtons, int x, int y, int team, GameCharacter character) {
        super(x, y, team);
        this.keys = keys;
        this.mouseButtons = mouseButtons;
        this.character = character;

        startX = x;
        startY = y;

        plevel = 0; // will be displayed in GUI as lev 1
        pexp = 0;
        maxHealth = 5;
        health = 5;
        psprint = 1.5;
        maxTimeSprint = 100;
        aimVector = new Vec2(0, 1);
        score = 0;
        weapon = new Rifle(this);
        setRailPricesAndImmortality();
    }
    
    /**
     * Handle creative mode
     */
    private void setRailPricesAndImmortality(){
    	if (Options.getAsBoolean(Options.CREATIVE)){
    		COST_RAIL = 0;
    		COST_DROID = 0;
    		COST_REMOVE_RAIL = 0;
    		isImmortal = true;
    	}else{
     		COST_RAIL = 10;
    		COST_DROID = 50;
    		COST_REMOVE_RAIL = 0;
    		isImmortal = false;
    	}
    }

    /**
     * Check if the player has reached enough XP for a levelup
     */
    private void handleLevelUp() {
        if (xpSinceLastLevelUp() >= nettoXpNeededForLevel(plevel+1)) {
            this.maxHealth++;
            plevel++;
            psprint += 0.1;
            maxTimeSprint += 20;

            MojamComponent.soundPlayer.playSound("/sound/levelUp.wav", (float) pos.x, (float) pos.y, true);
        }
    }

    /**
     * 
     * @param level to calculate summed up xp value for
     *
     * @return summed up xp value
     */
    public double summedUpXpNeededForLevel(int level){
        return (level * 7) * (level * 7);
    }
    
    /**
     * 
     * @param level to calculate netto xp value for
     *
     * @return netto xp value
     */
    public double nettoXpNeededForLevel(int level){
        if (level == 0) return 0;
        return summedUpXpNeededForLevel(level) - summedUpXpNeededForLevel(level-1);
    }
    
    /**
     * 
     * @return xp gained since last level up
     */
    public double xpSinceLastLevelUp(){
        return pexp - summedUpXpNeededForLevel(plevel);
    }
    
    @Override
    public void tick() {

        // If the mouse is used, update player orientation before level tick
        if (!mouseButtons.mouseHidden) {
            // Update player mouse, in world pixels relative to player
            setAimByMouse(
                    ((mouseButtons.getX() / MojamComponent.SCALE) - (MojamComponent.screen.w / 2)),
                    (((mouseButtons.getY() / MojamComponent.SCALE) + 24) - (MojamComponent.screen.h / 2)));
        } else {
            setAimByKeyboard();
        }

        time++;
		
		this.doRegenTime();
			
        handleLevelUp();
        flashMiniMapIcon();
        countdownTimers();
        playStepSound();

        double xa = 0;
        double ya = 0;
        double xaShot = 0;
        double yaShot = 0;

        // Handle keys
        if (!dead) {
            if (keys.up.isDown) {
                ya--;
            }
            if (keys.down.isDown) {
                ya++;
            }
            if (keys.left.isDown) {
                xa--;
            }
            if (keys.right.isDown) {
                xa++;
            }
            if (keys.right.isDown) {
                xa++;
            }
            if (keys.fireUp.isDown) {
                yaShot--;
            }
            if (keys.fireDown.isDown) {
                yaShot++;
            }
            if (keys.fireLeft.isDown) {
                xaShot--;
            }
            if (keys.fireRight.isDown) {
                xaShot++;
            }
        }

        // Handle mouse aiming
        if (!mouseAiming && !keys.fire.isDown && !mouseButtons.isDown(mouseFireButton) && xa * xa + ya * ya != 0) {
            aimVector.set(xa, ya);
            aimVector.normalizeSelf();
            updateFacing();
        }
        
        if (!mouseAiming && fireKeyIsDown() && xaShot * xaShot + yaShot * yaShot != 0) {
            aimVector.set(xaShot, yaShot);
            aimVector.normalizeSelf();
            updateFacing();
        }

        // Move player if it is not standing still
        if (xa != 0 || ya != 0) {
            handleMovement(xa, ya);
        }

        if (freezeTime > 0) {
            move(xBump, yBump);
        } else {
            move(xd + xBump, yd + yBump);

        }
        
        xd *= 0.4;
        yd *= 0.4;
        xBump *= 0.8;
        yBump *= 0.8;
        muzzleImage = (muzzleImage + 1) & 3;

        handleWeaponFire(xa, ya);

        int x = (int) pos.x / Tile.WIDTH;
        int y = (int) pos.y / Tile.HEIGHT;

        if (!dead && fallDownHole()) {
        	dead = true;
        	carrying = null;
        	deadDelay = 60;
        }

        if (dead && deadDelay <= 0) {
            dead = false;
            revive();
        }

        if (keys.build.isDown && !keys.build.wasDown) {
            handleRailBuilding(x, y);
        }

        if (carrying != null) {
            handleCarrying();
        } else {
            handleEntityInteraction();
        }

        if (isSeeing) {
            level.reveal(x, y, 5);
        }
    }

    /**
     * Update the display cycle of the player indicator on the minimap
     */
    private void flashMiniMapIcon() {
        minimapIcon = time / 3 % 4;
        if (minimapIcon == 3) {
            minimapIcon = 1;
        }
    }

    /**
     * Count down the internal timers
     */
    private void countdownTimers() {
        if (flashTime > 0) {
            flashTime = 0;
        }
        if (hurtTime > 0) {
            hurtTime--;
        }
        if (freezeTime > 0) {
            freezeTime--;
        }
        if (muzzleTicks > 0) {
            muzzleTicks--;
        }
        if (deadDelay > 0) {
            deadDelay--;
        }
    }

    /**
     * Play step sounds synchronized to player movement and carrying status
     */
    private void playStepSound() {
        if (keys.up.isDown || keys.down.isDown || keys.left.isDown || keys.right.isDown) {
            int stepCount = 25;
            
            if (carrying == null) {
                stepCount = 15;
            }
            
            if (isSprint) {
                stepCount *= 0.6;
            }
            
            if (steps % stepCount == 0) {
                MojamComponent.soundPlayer.playSound("/sound/Step " + (TurnSynchronizer.synchedRandom.nextInt(2) + 1) + ".wav", (float) pos.x, (float) pos.y, true);
            }
            steps++;
        }
    }

    /**
     * Handler player movement
     * 
     * @param xa Position change on the x axis
     * @param ya Position change on the y axis
     */
    private void handleMovement(double xa, double ya) {
        int facing2 = (int) ((Math.atan2(-xa, ya) * 8 / (Math.PI * 2) + 8.5)) & 7;
        int diff = facing - facing2;
        
        if (diff >= 4) {
            diff -= 8;
        }
        
        if (diff < -4) {
            diff += 8;
        }

        if (carrying != null) {
            if (diff > 2 || diff < -4) {
                walkTime--;
            } else {
                walkTime++;
            }
        }
        
        if (diff > 2 || diff < -4) {
            walkTime--;
        } else {
            walkTime++;
        }

        Random random = TurnSynchronizer.synchedRandom;
        if (walkTime >= nextWalkSmokeTick) {
            level.addEntity(new SmokePuffAnimation(this, Art.fxDust12, 35 + random.nextInt(10)));
            nextWalkSmokeTick += (15 + random.nextInt(15));
        }
        if (random.nextDouble() < 0.02f) {
            level.addEntity(new SmokePuffAnimation(this, Art.fxDust12, 35 + random.nextInt(10)));
        }

        double dd = Math.sqrt(xa * xa + ya * ya);
        double speed = getSpeed() / dd;

        if (this.keys.sprint.isDown) {
            if (timeSprint < maxTimeSprint) {
                isSprint = true;
                if (carrying == null) {
                    speed = getSpeed() / dd * psprint;
                } else {
                    speed = getSpeed() / dd * (psprint - 0.5);
                }
                timeSprint++;
            } else {
                isSprint = false;
            }
        } else {
            if (timeSprint >= 0) {
                timeSprint--;
            }
            isSprint = false;
        }

        xa *= speed;
        ya *= speed;

        xd += xa;
        yd += ya;
    }

    /**
     * Handle weapon fire
     * 
     * @param xa Position change on the x axis
     * @param ya Position change on the y axis
     */
    private void handleWeaponFire(double xa, double ya) {
        weapon.weapontick();
        
        if (!dead
                && (carrying == null && fireKeyIsDown()
                || carrying == null && mouseButtons.isDown(mouseFireButton))) {
            wasShooting = true;
            if (takeDelay > 0) {
                takeDelay--;
            }
            weapon.primaryFire(xa, ya);
        } else {
            if (wasShooting) {
                suckRadius = 0;
            } else {
            	suckRadius = 60;
            }
            wasShooting = false;
            takeDelay = 15;
        }
    }
    
    /**
     * Returns true if one of the keyboard fire buttons is down
     * @return
     */
    private boolean fireKeyIsDown() {
        return keys.fire.isDown || keys.fireUp.isDown || keys.fireDown.isDown || keys.fireRight.isDown || keys.fireLeft.isDown;
    }

    /**
     * Handle rail building
     * 
     * @param x current position's X coordinate
     * @param y current position's Y coordinate
     */
    private void handleRailBuilding(int x, int y) {
    	boolean outsideLevel = y < 8 || y > level.height - 9;
        if (level.getTile(x, y).isBuildable()) {
        	
            if (score >= COST_RAIL && time - lastRailTick >= RailDelayTicks && !outsideLevel) {
                lastRailTick = time;
                level.placeTile(x, y, new RailTile(level.getTile(x, y)), this);
                payCost(COST_RAIL);
            } else if (score < COST_RAIL && this.team == MojamComponent.localTeam) {
            	Notifications.getInstance().add(MojamComponent.texts.buildRail(COST_RAIL));
            }            
        } else if (level.getTile(x, y) instanceof RailTile) {
            if ((y < 8 && team == Team.Team2) || (y > level.height - 9 && team == Team.Team1)) {
                if (score >= COST_DROID) {
                    level.addEntity(new RailDroid(pos.x, pos.y, team));
                    payCost(COST_DROID);
                } else {
                	if(this.team == MojamComponent.localTeam) {
                		Notifications.getInstance().add(MojamComponent.texts.buildDroid(COST_DROID));
                	}
                }
            } else {

                if (score >= COST_REMOVE_RAIL && time - lastRailTick >= RailDelayTicks) {
                    lastRailTick = time;
                    if (((RailTile) level.getTile(x, y)).remove()) {
                        payCost(COST_REMOVE_RAIL);
                    }
                } else if (score < COST_REMOVE_RAIL) {
                	if(this.team == MojamComponent.localTeam) {
                		Notifications.getInstance().add(MojamComponent.texts.removeRail(COST_REMOVE_RAIL));
                	}
                }
                MojamComponent.soundPlayer.playSound("/sound/Track Place.wav", (float) pos.x, (float) pos.y);
            }
        }
    }

    /**
     * Handle object carrying
     */
    private void handleCarrying() {

        carrying.setPos(pos.x, pos.y - 20);
        carrying.tick();
        if (keys.use.wasPressed() || mouseButtons.isDown(mouseUseButton)) {
            mouseButtons.setNextState(mouseUseButton, false);

            if (((IUsable) carrying).isAllowedToCancel()) {
                drop();
            }
        }
    }

    /**
     * Handle interaction with entities
     */
    private void handleEntityInteraction() {
        // Unhighlight previously selected building
        if (selected != null) {
            selected.setHighlighted(false);
            selected = null;
        }

        // Find the closest Building within interation
        // distance
        Building closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity e : level.getEntitiesSlower(pos.x - INTERACT_DISTANCE, pos.y - INTERACT_DISTANCE, pos.x + INTERACT_DISTANCE, pos.y + INTERACT_DISTANCE, Building.class)) {
            Building b = (Building)e;
            double dist = e.pos.distSqr(getInteractPosition());
            if (dist <= INTERACT_DISTANCE && dist < closestDist) {
                closestDist = dist;
                closest = b;
            }
        }

        // If we found a building close enough to interact with...
        if (closest != null) {
            // Perform any allowed interactions if the correct
            // keys have been pressed
            if (keys.use.wasPressed() || mouseButtons.isDown(mouseUseButton)) {

                if (canUseBuilding(closest)) {
                    closest.use(this);
                    mouseButtons.setNextState(mouseUseButton, false);
                }
            } else if (keys.upgrade.wasPressed()) {
                
                if (canUpgradeBuilding(closest)) {
                    closest.upgrade(this);
                }
            }
            
            // If it is a building we should highlight on this game
            // client, then highlight the building (also, remember the
            // highlighted building, so we can unhighlight it again later)
            if (shouldHighlightBuildingOnThisGameClient(closest)) {
                selected = closest;
                selected.setHighlighted(true);
            }
        }
    }
    
    // Whether this Player should see the building in question
    // highlighted on their game client - this indicates that
    // they can interact with the building
    private boolean shouldHighlightBuildingOnThisGameClient(Building building) {
        return building.isHighlightable() && canInteractWithBuilding(building) && this.team == MojamComponent.localTeam; 
    }
    
    // Whether this Player is allowed to use the building in 
    // question
    private boolean canUseBuilding(Building building) {
        //return building.team == this.team || building.team == Team.Neutral; // Players can only use their own and neutral buildings
        return !(building instanceof ShopItem && building.team != this.team); // Players can only use their own shops, but can use any other building regardless of ownership
    }
    
    // Whether this Player is allowed to upgrade the building
    // in question
    private boolean canUpgradeBuilding(Building building) {
        return building.team == this.team; // Players can only upgrade their own buildings
    }
    
    // Whether this Player is allowed to interact with the building in 
    // question
    private boolean canInteractWithBuilding(Building building) {
        return canUseBuilding(building) || canUpgradeBuilding(building);
    }

    /**
     * Pay for an item
     * 
     * @param cost Item cost
     */
    public void payCost(int cost) {
        score -= cost;

        while (cost > 0) {
            double dir = TurnSynchronizer.synchedRandom.nextDouble() * Math.PI * 2;
            Loot loot = new Loot(pos.x, pos.y, Math.cos(dir), Math.sin(dir), cost / 2);
            loot.makeUntakeable();
            level.addEntity(loot);

            cost -= loot.getScoreValue();
        }
    }

    /**
     * Add score points
     * 
     * @param points Points
     */
    public void addScore(int points) {
        if (points > 0) {
            score += points;
        }
    }

    /**
     * Drop all money. Animated, loot items will fall on the floor.
     */
    public void dropAllMoney() {
        score /= 2;
        
        while (score > 0) {
            double dir = TurnSynchronizer.synchedRandom.nextDouble() * Math.PI * 2;
            Loot loot = new Loot(pos.x, pos.y, Math.cos(dir), Math.sin(dir), score / 2);
            level.addEntity(loot);

            score -= loot.getScoreValue();
        }
        score = 0;
    }

    @Override
    public void render(Screen screen) {
		Bitmap[][] sheet = Art.getPlayer(getCharacter());
    	
		if(sheet == null){
			return;
		}
		
        if (dead) {
            // don't draw anything if we are dead (in a hole)
            return;
        }

        int frame = (walkTime / 4 % 6 + 6) % 6;

        int facing = this.facing + (carrying != null ? 8 : 0);
        double xmuzzle = muzzleX + ((facing == 0) ? 4 : 0);
        double ymuzzle = muzzleY - ((facing == 0) ? 4 : 0);

        boolean behind = (facing >= 3 && facing <= 5);

        if (muzzleTicks > 0 && behind) {
            screen.blit(Art.muzzle[muzzleImage][0], xmuzzle, ymuzzle);
        }

        if (hurtTime % 2 != 0) {
            screen.colorBlit(sheet[frame][facing], pos.x - Tile.WIDTH / 2, pos.y - Tile.HEIGHT / 2 - 8, 0x80ff0000);
        } else if (flashTime > 0) {
            screen.colorBlit(sheet[frame][facing], pos.x - Tile.WIDTH / 2, pos.y - Tile.HEIGHT / 2 - 8, 0x80ffff80);
        } else {
            screen.blit(sheet[frame][facing], pos.x - Tile.WIDTH / 2, pos.y - Tile.HEIGHT / 2 - 8);
        }

        if (muzzleTicks > 0 && !behind) {
            screen.blit(Art.muzzle[muzzleImage][0], xmuzzle, ymuzzle);
        }
	}
    
    public void setCharacter(GameCharacter character) {
		this.character = character;
	}
    
    public GameCharacter getCharacter(){
    	return character;
    }

	@Override
	public void renderTop(Screen screen) {
		int frame = (walkTime / 4 % 6 + 6) % 6;
		renderCarrying(screen, (frame == 0 || frame == 3) ? -1 : 0);
	}
    
    @Override
    protected void renderCarrying(Screen screen, int yOffs) {
    	if(carrying != null && carrying.team == MojamComponent.localTeam ) {
			if(carrying instanceof Turret) {
				Turret turret = (Turret)carrying;
				turret.drawRadius(screen);	
			} else if(carrying instanceof Harvester) {
				Harvester harvester = (Harvester)carrying;
				harvester.drawRadius(screen);	
			}//TODO make an interface to clean this up
       	}
		
    	super.renderCarrying(screen, yOffs);
    }

    @Override
    public void collide(Entity entity, double xa, double ya) {
        xd += xa * 0.4;
        yd += ya * 0.4;
    }

    @Override
    public void take(Loot loot) {
        loot.remove();
        level.addEntity(new Sparkle(pos.x, pos.y, -1, 0));
        level.addEntity(new Sparkle(pos.x, pos.y, +1, 0));
        score += loot.getScoreValue();
    }

    @Override
    public double getSuckPower() {
        return suckRadius / 60.0;
    }

    @Override
    public boolean canTake() {
        return takeDelay > 0;
    }

    @Override
    public void flash() {
        flashTime = 20;
    }

    @Override
    public int getScore() {
        return score;
    }

    @Override
    public Bitmap getSprite() {
        return null;
    }

    public boolean useMoney(int cost) {
        if (cost > score) {
            return false;
        }

        score -= cost;
        return true;
    }

    private Vec2 getInteractPosition() {
        return pos.add(new Vec2(Math.cos((facing) * (Math.PI) / 4 + Math.PI / 2), Math.sin((facing) * (Math.PI) / 4 + Math.PI / 2)).scale(30));
    }


    public void drop() {
        carrying.xSlide = aimVector.x * 5;
        carrying.ySlide = aimVector.y * 5;
        super.drop();
    }

    /**
     * Set player orientation
     * 
     * @param facing New Orientation
     */
    public void setFacing(int facing) {
        this.facing = facing;
    }

    @Override
    protected boolean shouldBlock(Entity e) {
        if (carrying != null && e instanceof Bullet && ((Bullet) e).owner == carrying) {
            return false;
        }
        return true;
    }

    public void setCanSee(boolean b) {
        this.isSeeing = b;
    }

    @Override
    public void notifySucking() {
    }

    @Override
    public void hurt(Entity source, float damage) {
        if (isImmortal) {
            return;
        }

        if (hurtTime == 0) {
            hurtTime = 25;
            freezeTime = 15;
            health -= damage;
            
            if (health <= 0) {
                revive();
            } else {

                double dist = source.pos.dist(pos);
                xBump = (pos.x - source.pos.x) / dist * 10;
                yBump = (pos.y - source.pos.y) / dist * 10;

                MojamComponent.soundPlayer.playSound("/sound/hit2.wav", (float) pos.x, (float) pos.y, true);
            }
        }
    }

    /**
     * Revive the player. Carried items are lost, as is all the money.
     */
    private void revive() {
        Notifications.getInstance().add(MojamComponent.texts.hasDiedCharacter(getCharacter()));
        carrying = null;
        dropAllMoney();
        pos.set(startX, startY);
        health = maxHealth;
    }

    @Override
    public void hurt(Bullet bullet) {
        hurt(bullet, 1);
    }

    @Override
    public String getDeathSound() {
        return "/sound/Death.wav";
    }

    /**
     * Orientate the player in the direction of the given mouse coordinates
     * 
     * @param x X coordinate
     * @param y Y coordinate
     */
    public void setAimByMouse(int x, int y) {
        mouseAiming = true;
        aimVector.set(x, y);
        aimVector.normalizeSelf();
        updateFacing();
    }

    /**
     * Disable mouse aiming and activate keyboard aiming
     */
    public void setAimByKeyboard() {
        mouseAiming = false;
    }

    /**
     * Update player orientation for rendering
     */
    public void updateFacing() {
        facing = (int) ((Math.atan2(-aimVector.x, aimVector.y) * 8 / (Math.PI * 2) + 8.5)) & 7;
    }



}
