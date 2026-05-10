package io.anuke.mindustry.desktop;

import com.badlogic.gdx.utils.Base64Coder;
import io.anuke.kryonet.DefaultThreadImpl;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.core.ThreadHandler.ThreadProvider;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.UCore;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.util.Strings;

import javax.swing.*;
import java.net.NetworkInterface;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Random;

import static io.anuke.mindustry.Vars.*;

public class DesktopPlatform extends Platform {
    final static boolean useDiscord = UCore.getPropertyNotNull("sun.arch.data.model").equals("64");
    final static String applicationId = "398246104468291591";
    final static DateFormat format = SimpleDateFormat.getDateTimeInstance();
    String[] args;

    private Object discordRpc;
    private Class<?> discordPresenceClass;

    public DesktopPlatform(String[] args){
        this.args = args;

        if(useDiscord) {
            initDiscord();
        }
    }

    @Override
    public String format(Date date){
        return format.format(date);
    }

    @Override
    public String format(int number){
        return NumberFormat.getIntegerInstance().format(number);
    }

    @Override
    public void showError(String text){
        JOptionPane.showMessageDialog(null, text);
    }

    @Override
    public String getLocaleName(Locale locale){
        return locale.getDisplayName(locale);
    }

    @Override
    public void updateRPC() {
        if(discordRpc == null) return;

        Object presence = newDiscordPresence();
        if(presence == null) return;

        if(!state.is(State.menu)){
            setDiscordPresenceField(presence, "state", Strings.capitalize(state.mode.name()) + ", Solo");
            setDiscordPresenceField(presence, "details", Strings.capitalize(world.getMap().name) + " | Wave " + state.wave);
            setDiscordPresenceField(presence, "largeImageText", "Wave " + state.wave);

            if(Net.active()){
                setDiscordPresenceField(presence, "partyMax", 16);
                setDiscordPresenceField(presence, "partySize", playerGroup.size());
                setDiscordPresenceField(presence, "state", Strings.capitalize(state.mode.name()));
            }
        }else{
            if(ui.editor != null && ui.editor.isShown()){
                setDiscordPresenceField(presence, "state", "In Editor");
            }else {
                setDiscordPresenceField(presence, "state", "In Menu");
            }
        }

        setDiscordPresenceField(presence, "largeImageKey", "logo");

        try{
            discordRpc.getClass().getMethod("Discord_UpdatePresence", discordPresenceClass).invoke(discordRpc, presence);
        }catch(Throwable ignored){
        }
    }

    @Override
    public void onGameExit() {
        if(discordRpc == null) return;
        try{
            discordRpc.getClass().getMethod("Discord_Shutdown").invoke(discordRpc);
        }catch(Throwable ignored){
        }
    }

    @Override
    public boolean isDebug() {
        return args.length > 0 && args[0].equalsIgnoreCase("-debug");
    }

    @Override
    public ThreadProvider getThreadProvider() {
        return new DefaultThreadImpl();
    }

    @Override
    public byte[] getUUID() {
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            NetworkInterface out;
            for(out = e.nextElement(); out.getHardwareAddress() == null && e.hasMoreElements() && validAddress(out.getHardwareAddress()); out = e.nextElement());

            byte[] bytes = out.getHardwareAddress();
            byte[] result = new byte[8];
            System.arraycopy(bytes, 0, result, 0, bytes.length);

            if(new String(Base64Coder.encode(result)).equals("AAAAAAAAAOA=")) throw new RuntimeException("Bad UUID.");

            return result;
        }catch (Exception e){
            Settings.defaults("uuid", "");

            String uuid = Settings.getString("uuid");
            if(uuid.isEmpty()){
                byte[] result = new byte[8];
                new Random().nextBytes(result);
                uuid = new String(Base64Coder.encode(result));
                Settings.putString("uuid", uuid);
                Settings.save();
                return result;
            }
            return Base64Coder.decode(uuid);
        }
    }

    private boolean validAddress(byte[] bytes){
        byte[] result = new byte[8];
        System.arraycopy(bytes, 0, result, 0, bytes.length);
        return !new String(Base64Coder.encode(result)).equals("AAAAAAAAAOA=");
    }

    private void initDiscord(){
        try{
            Class<?> rpcClass = Class.forName("club.minnced.discord.rpc.DiscordRPC");
            Class<?> handlersClass = Class.forName("club.minnced.discord.rpc.DiscordEventHandlers");
            discordPresenceClass = Class.forName("club.minnced.discord.rpc.DiscordRichPresence");

            discordRpc = rpcClass.getField("INSTANCE").get(null);
            Object handlers = handlersClass.getConstructor().newInstance();

            rpcClass.getMethod("Discord_Initialize", String.class, handlersClass, boolean.class, String.class)
                    .invoke(discordRpc, applicationId, handlers, true, "");
        }catch(Throwable ignored){
            discordRpc = null;
            discordPresenceClass = null;
        }
    }

    private Object newDiscordPresence(){
        try{
            return discordPresenceClass.getConstructor().newInstance();
        }catch(Throwable ignored){
            return null;
        }
    }

    private void setDiscordPresenceField(Object presence, String field, Object value){
        try{
            discordPresenceClass.getField(field).set(presence, value);
        }catch(Throwable ignored){
        }
    }
}
