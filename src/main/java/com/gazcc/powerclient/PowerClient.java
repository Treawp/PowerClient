package com.gazcc.powerclient;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod(modid = PowerClient.MODID, name = PowerClient.NAME, version = PowerClient.VERSION)
public class PowerClient {

    public static final String MODID   = "powerclient";
    public static final String NAME    = "PowerClient";
    public static final String VERSION = "1.0.0";
    public static ModuleManager moduleManager;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        moduleManager = new ModuleManager();
        System.out.println("[PowerClient] Aktif!");
    }

    public static class RotationUtil {
        private static final Minecraft mc = Minecraft.getMinecraft();

        public static float[] getRotationsToEntity(Entity target) {
            double dx   = target.posX - mc.thePlayer.posX;
            double dz   = target.posZ - mc.thePlayer.posZ;
            double dy   = (target.posY + target.getEyeHeight() / 2.0)
                        - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
            double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
            float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
            return new float[]{ yaw, pitch };
        }

        public static float[] smoothRotation(float[] from, float[] to, float speed) {
            float yaw   = from[0] + (wrapDegrees(to[0] - from[0]) * speed);
            float pitch = from[1] + ((to[1] - from[1]) * speed);
            pitch = MathHelper.clamp_float(pitch, -90f, 90f);
            return new float[]{ yaw, pitch };
        }

        public static float wrapDegrees(float deg) {
            deg %= 360f;
            if (deg >= 180f)  deg -= 360f;
            if (deg < -180f)  deg += 360f;
            return deg;
        }

        public static void applyRotation(float yaw, float pitch) {
            mc.thePlayer.rotationYaw   = yaw;
            mc.thePlayer.rotationPitch = pitch;
        }
    }

    public static abstract class Module {
        protected static final Minecraft mc = Minecraft.getMinecraft();
        private final String  name;
        private final int     key;
        private boolean       enabled = false;

        public Module(String name, int key) {
            this.name = name;
            this.key  = key;
        }

        public void toggle() {
            enabled = !enabled;
            if (enabled) {
                MinecraftForge.EVENT_BUS.register(this);
                onEnable();
            } else {
                MinecraftForge.EVENT_BUS.unregister(this);
                onDisable();
            }
        }

        public void onEnable()  {}
        public void onDisable() {}
        public String  getName()   { return name; }
        public int     getKey()    { return key; }
        public boolean isEnabled() { return enabled; }
    }

    public static class MobAura extends Module {
        private float   reach         = 4.0f;
        private float   rotSpeed      = 0.8f;
        private boolean targetAnimals = false;
        private int     attackDelay   = 10;
        private int     tickTimer     = 0;

        public MobAura() { super("MobAura", Keyboard.KEY_R); }

        @Override public void onEnable()  { System.out.println("[MobAura] ON!"); }
        @Override public void onDisable() { System.out.println("[MobAura] OFF"); }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START)        return;
            if (mc.theWorld == null || mc.thePlayer == null) return;
            if (mc.thePlayer.isDead)                         return;

            Entity target = getNearestTarget();
            if (target == null) return;

            float[] targetRot = RotationUtil.getRotationsToEntity(target);
            float[] smoothed  = RotationUtil.smoothRotation(
                new float[]{ mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch },
                targetRot, rotSpeed
            );
            RotationUtil.applyRotation(smoothed[0], smoothed[1]);

            if (++tickTimer >= attackDelay) {
                tickTimer = 0;
                mc.playerController.attackEntity(mc.thePlayer, target);
                mc.thePlayer.swingItem();
            }
        }

        private Entity getNearestTarget() {
            return mc.theWorld.loadedEntityList.stream()
                .filter(e -> {
                    if (e == mc.thePlayer)                            return false;
                    if (!e.isEntityAlive())                           return false;
                    if (mc.thePlayer.getDistanceToEntity(e) > reach) return false;
                    if (e instanceof IMob)                            return true;
                    return targetAnimals && e instanceof EntityAnimal;
                })
                .min(Comparator.comparingDouble(mc.thePlayer::getDistanceToEntity))
                .orElse(null);
        }
    }

    public static class ModuleManager {
        private static final Minecraft mc = Minecraft.getMinecraft();
        private final List<Module> modules = new ArrayList<>();

        public ModuleManager() {
            modules.add(new MobAura());
            MinecraftForge.EVENT_BUS.register(this);
        }

        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            if (mc.thePlayer == null || mc.currentScreen != null) return;
            for (Module m : modules) {
                if (Keyboard.isKeyDown(m.getKey())) {
                    m.toggle();
                }
            }
        }

        public List<Module> getModules() { return modules; }
    }
}
