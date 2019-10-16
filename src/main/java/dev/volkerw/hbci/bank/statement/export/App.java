package dev.volkerw.hbci.bank.statement.export;

import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.callback.AbstractHBCICallback;
import org.kapott.hbci.manager.BankInfo;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIVersion;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;

import static dev.volkerw.hbci.bank.statement.export.App.IO.*;

public class App {
    static ResourceBundle resourceBundle = ResourceBundle.getBundle("locale.Messages", Locale.getDefault());

    public static void main(String[] args) {
        String blz, user, pin;

        blz = readLine("blz");
        user = readLine("user");
        pin = readPassword("pin");

        Hbci hbci = Hbci.initialize(blz, user, pin);
        BankInfo bankInfo = hbci.getBankInfo(blz);

        Konto[] accounts = hbci.getAccounts();

        display("account");
        for (int i = 0; i < accounts.length; i++) {
            displayString("[%s] %s", i, accounts[i]);
        }
        int option = Integer.valueOf(IO.readLine()).intValue();

        List<GVRKUms.UmsLine> turnovers = hbci.getTurnovers(accounts[option]);

        display("turnovers");
        for (GVRKUms.UmsLine turnover : turnovers) {
            displayString(turnover.toString());
        }

        readLine("Beenden? &s", "[y]");
    }

    static class IO {
        public static String readPassword(String key) {
            String message = resourceBundle.getString(key);
            return readPassword(message, new Object[]{});
        }

        public static String readPassword(String message, Object... args) {
            Console console = console();
            if (console != null) {
                return new String(console.readPassword(message + "%n", args));
            } else {
                System.out.printf(message + "%n", args);
                return scanner().nextLine();
            }
        }

        public static String readLine(String key) {
            String message = resourceBundle.getString(key);
            return readLine(message, new Object[]{});
        }

        public static String readLine(String message, Object... args) {
            Console console = console();
            if (console != null) {
                return console.readLine(message + "%n", args);
            } else {
                System.out.printf(message + "%n", args);
                return scanner().nextLine();
            }
        }

        public static Scanner scanner() {
            return new Scanner(System.in);
        }

        private static Console console() {
            return System.console();
        }

        public static void display(String key) {
            String message = resourceBundle.getString(key);
            displayString(message);
        }

        public static void displayString(String s) {
            System.out.println(s);
        }

        public static void displayString(String s, Object... args) {
            System.out.printf(s + "%n", args);
        }

        public static String readLine() {
            Console console = console();
            if (console != null) {
                return console.readLine();
            }
            return scanner().nextLine();
        }
    }

    static class Hbci {
        private final HBCIPassport passport;

        private Hbci(HBCIPassport passport) {
            this.passport = passport;
        }

        public static Hbci initialize(String blz, String user, String pin) {
            HBCIUtils.init(new Properties(), new MyHBCICallback(blz, user, pin));
            HBCIPassport passport = createPassport(blz, user);
            return new Hbci(passport);
        }

        public static HBCIPassport createPassport(String blz, String user) {
            try {
                HBCIUtils.setParam("client.passport.default", "PinTan");
                HBCIUtils.setParam("client.passport.PinTan.init", "1");

                File passportFile = Files.createTempFile(blz, user).toFile();
                passportFile.deleteOnExit();

                HBCIPassport passport = AbstractHBCIPassport.getInstance(passportFile);
                passport.setCountry(Locale.getDefault().getCountry());
                return passport;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public BankInfo getBankInfo(String blz) {
            try {
                return HBCIUtils.getBankInfo(blz);
            } catch (Exception e) {
                System.err.println(e);
                throw new RuntimeException(e);
            }
        }

        public Konto[] getAccounts() {

            return executeJob(handle -> {
                Konto[] konten = passport.getAccounts();
                if (konten == null || konten.length == 0) { // TODO
                }
                return konten;
            });
        }

        public List<GVRKUms.UmsLine> getTurnovers(Konto account) {
            return executeJob(handle -> {
                HBCIJob umsatzJob = handle.newJob("KUmsAll");
                umsatzJob.setParam("my", account);
                umsatzJob.addToQueue();

                HBCIExecStatus status = handle.execute();

                if (!status.isOK()) {
                    // TODO error(status.toString());
                }

                GVRKUms result = (GVRKUms) umsatzJob.getJobResult();

                // Pruefen, ob der Abruf der Umsaetze geklappt hat
                if (!result.isOK()) {
                    // TODO error(result.toString());
                }

                return result;
            }).getFlatData();
        }

        private <T> T executeJob(Function<HBCIHandler, T> job) {
            HBCIHandler handle = null;
            try {
                handle = new HBCIHandler(HBCIVersion.HBCI_300.getId(), passport);
                return job.apply(handle);
            } finally {
                if (handle != null) {
                    handle.close();
                }
            }
        }

        private static class MyHBCICallback extends AbstractHBCICallback {
            private final String blz;
            private final String user;
            private final String pin;

            public MyHBCICallback(String blz, String user, String pin) {
                this.blz = blz;
                this.user = user;
                this.pin = pin;
            }

            @Override
            public void log(String msg, int level, Date date, StackTraceElement trace) {
                // Ausgabe von Log-Meldungen bei Bedarf
                // System.out.println(msg);
            }

            @Override
            public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
                // System.out.println("LOG " + msg + retData.toString());
                switch (reason) {
                    case NEED_PASSPHRASE_LOAD:
                    case NEED_PASSPHRASE_SAVE:
                        retData.replace(0, retData.length(), pin);
                        break;
                    case NEED_PT_PIN:
                        retData.replace(0, retData.length(), pin);
                        break;
                    case NEED_BLZ:
                        retData.replace(0, retData.length(), blz);
                        break;
                    case NEED_USERID:
                    case NEED_CUSTOMERID:
                        retData.replace(0, retData.length(), user);
                        break;
                    case NEED_PT_SECMECH:
                        String options = retData.toString();
                        // TODO
                        String code = "942";
                        retData.replace(0, retData.length(), code);
                        break;
                    case NEED_PT_TAN:

                        String tan = IO.scanner().next();
                        retData.replace(0, retData.length(), tan);
                        break;
                    case HAVE_ERROR:
                        // TODO
                        System.err.println(msg);
                        break;
                    default:
                        break;

                }
            }

            @Override
            public void status(HBCIPassport passport, int statusTag, Object[] o) {
                // So aehnlich wie log(String,int,Date,StackTraceElement) jedoch fuer Status-Meldungen.
            }

        }
    }
}
