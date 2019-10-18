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
import org.kapott.hbci.structures.Saldo;
import org.kapott.hbci.structures.Value;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

        Konto self = accounts[option];
        List<GVRKUms.UmsLine> turnovers = hbci.getTurnovers(self);

        display("turnovers");
        for (GVRKUms.UmsLine turnover : turnovers) {
            displayString(turnover.toString());
        }
        if (shouldSave()) {
            String file = getFileName() + ".csv";
            List<String> rows = new ArrayList<>();

            rows.add(String.join(";", "id", "bdate", "valuta", "value.longValue", "value.curr") //
                    + ";" + String.join(";", "saldo.timestamp", "saldo.value.longValue", "saldo.value.curr") //
                    + ";" + String.join(";", "other.acctype", "other.allowedGVs", "other.bic", "other.blz", "other.country", "other.curr", "other.customerid", "other.iban", "other.limit", "other.name", "other.name2", "other.number", "other.subnumber", "other.type") //
                    + ";" + String.join(";","text", "usage") //
                    + ";" + String.join(";", "mandateId", "primanota", "gvcode", "purposecode", "instref", "customerref", "endToEndId", "addkey", "additional")
                    + ";" + String.join(";", "isCamt", "isSepa", "isStorno") //
                    + ";" + String.join(";", "charge_value.longValue", "charge_value.curr") //
                    + ";" + String.join(";", "orig_value.longValue", "orig_value.curr"));

            for (GVRKUms.UmsLine t : turnovers) {
                Value value = Optional.ofNullable(t.value).orElseGet(Value::new);
                Value charge_value = Optional.ofNullable(t.charge_value).orElseGet(Value::new);
                Value orig_value = Optional.ofNullable(t.orig_value).orElseGet(Value::new);
                Saldo saldo = Optional.ofNullable(t.saldo).orElseGet(Saldo::new);
                Value saldo_value = Optional.ofNullable(saldo.value).orElseGet(Value::new);
                Konto other = Optional.ofNullable(t.other).orElseGet(Konto::new);

                rows.add(String.join(";", toString(t.id, t.bdate, t.valuta, value.getLongValue(), value.getCurr())) //
                        + ";" + String.join(";", toString(saldo.timestamp, saldo_value.getLongValue(), saldo_value.getCurr())) //
                        + ";" + String.join(";", toString(other.acctype, other.allowedGVs, other.bic, other.blz, other.country, other.curr, other.customerid, other.iban, other.limit, other.name, other.name2, other.number, other.subnumber, other.type)) //
                        + ";" + String.join(";", toString(t.text, String.join(" ", t.usage))) //
                        + ";" + String.join(";", toString(t.mandateId, t.primanota, t.gvcode, t.purposecode, t.instref, t.customerref, t.endToEndId, t.addkey, t.additional)) //
                        + ";" + String.join(";", toString(t.isCamt, t.isSepa, t.isStorno)) //
                        + ";" + String.join(";", toString(charge_value.getLongValue(), charge_value.getCurr())) //
                        + ";" + String.join(";", toString(orig_value.getLongValue(), orig_value.getCurr())));
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(new File(file)))) {
                rows.forEach(row -> {
                    writer.println(row);
                });
            } catch (IOException e) {
                displayString("%s", e);
            }
        }

        readLine("Beenden? %s", "[y]");
    }

    private static String[] toString(Object... args) {
        return Arrays.stream(args).map(it -> {
            if (it instanceof Date) {
                return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(((Date) it).getTime())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime());
            }
            return it;
        }).map(s -> s == null ? "" : s.toString()).toArray(String[]::new);
    }

    private static String getFileName() {
        String file = readLine("file");
        if (file.isEmpty()) {
            return getFileName();
        }
        return file;
    }

    private static boolean shouldSave() {
        String save = readLine("save") + "";
        boolean shouldSave = false;
        if (save.equalsIgnoreCase("y") || save.equalsIgnoreCase("true") || save.equalsIgnoreCase("j") || save.isEmpty()) {
            shouldSave = true;
        }
        return shouldSave;
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
                if (account.iban == null || account.iban.isEmpty()) {
                    umsatzJob.setParam("my.iban", readLine("iban"));
                }
                if (account.bic == null || account.bic.isEmpty()) {
                    umsatzJob.setParam("my.bic", readLine("bic"));
                }
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
                        display("tan-option");
                        String code = readLine(options, new Object[]{});
                        retData.replace(0, retData.length(), code);
                        break;
                    case NEED_PT_TAN:
                        retData.replace(0, retData.length(), readLine("tan"));
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
