import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// ─────────────────────────────────────────────
//  Model: Patient
// ─────────────────────────────────────────────
class Patient {

    // Severity: lower number = higher priority
    // 1 = Critical, 2 = Urgent, 3 = Moderate, 4 = Minor
    public enum Severity {
        CRITICAL(1, "🔴 CRITICAL"),
        URGENT  (2, "🟠 URGENT"),
        MODERATE(3, "🟡 MODERATE"),
        MINOR   (4, "🟢 MINOR");

        public final int    level;
        public final String label;

        Severity(int level, String label) {
            this.level = level;
            this.label = label;
        }
    }

    private static int idCounter = 1000;
    private double survivalChance;

    private final int      id;
    private final String   name;
    private final Severity severity;
    private final String   condition;
    private final long     arrivalTime;    // ms — used as tiebreaker & wait-time start
    private       long     treatmentTime;  // ms — stamped when treatment begins

    public Patient(String name, Severity severity, String condition) {
        this.id            = ++idCounter;
        this.name          = name;
        this.severity      = severity;
        this.condition     = condition;
        this.arrivalTime   = System.currentTimeMillis();
        this.treatmentTime = -1;
        this.survivalChance = 50.0; // base survival
    }

    // ── Getters ──────────────────────────────
    public int      getId()          { return id; }
    public String   getName()        { return name; }
    public Severity getSeverity()    { return severity; }
    public String   getCondition()   { return condition; }
    public long     getArrivalTime() { return arrivalTime; }

    public void markTreatmentStart() { this.treatmentTime = System.currentTimeMillis(); }

    public long getWaitTimeMs() {
        return (treatmentTime < 0) ? -1 : (treatmentTime - arrivalTime);
    }

    public String formatWaitTime() {
        long ms = getWaitTimeMs();
        if (ms < 0)     return "not treated yet";
        if (ms < 1_000) return ms + " ms";
        long secs = ms / 1_000;
        long mins = secs / 60;
        secs %= 60;
        if (mins > 0)   return mins + " min " + secs + " sec";
        return secs + " sec";
    }

    public void improveSurvival(HospitalResources res) {
        double boost = 0;
        switch (severity) {
            case CRITICAL:  boost = 20; break;
            case URGENT:    boost = 10; break;
            case MODERATE:  boost = 5;  break;
            case MINOR:     boost = 2;  break;
        }
        if (res.useBloodPack()) {
            survivalChance = Math.min(100, survivalChance + boost);
            System.out.printf("  🩸 Blood pack used for %s → survival now %.1f%%%n", name, survivalChance);
        } else {
            System.out.printf("  ⚠️  No blood packs left for %s — survival remains %.1f%%%n", name, survivalChance);
        }
    }
}

// ─────────────────────────────────────────────
//  Model: HospitalResources
// ─────────────────────────────────────────────
class HospitalResources {
    private int bloodPacks;
    private int food;
    private int water;

    public HospitalResources() {
        // Randomize starting resources
        this.bloodPacks = (int) (Math.random() * 5) + 3;  // 3–7 packs
        this.food       = (int) (Math.random() * 30) + 20; // 20–49 units
        this.water      = (int) (Math.random() * 30) + 20;
    }

    public int getCapacity() {
        // Capacity scaled by total sustenance
        return (food + water) / 10; // e.g. 4–10 patients
    }

    public boolean useBloodPack() {
        if (bloodPacks > 0) {
            bloodPacks--;
            return true;
        }
        return false;
    }

    public void printStatus() {
        System.out.printf("  🩸 Blood packs: %d%n", bloodPacks);
        System.out.printf("  🍞 Food:        %d%n", food);
        System.out.printf("  💧 Water:       %d%n", water);
        System.out.printf("  🏥 Capacity:    %d patients%n", getCapacity());
    }
}


// ─────────────────────────────────────────────
//  Enum: PriorityMode
// ─────────────────────────────────────────────
enum PriorityMode {
    SEVERITY    ("By Severity (Critical first)"),
    ARRIVAL     ("By Arrival Time (FIFO — first come, first served)"),
    ALPHABETICAL("By Name (A → Z)");

    public final String description;
    PriorityMode(String description) { this.description = description; }

    /** Build the correct Comparator for each mode. */
    public Comparator<Patient> comparator() {
        switch (this) {
            case SEVERITY:
                // Primary: severity level (lower = more critical)
                // Tiebreaker: earlier arrival wins
                return Comparator
                        .comparingInt((Patient p) -> p.getSeverity().level)
                        .thenComparingLong(Patient::getArrivalTime);
            case ARRIVAL:
                return Comparator.comparingLong(Patient::getArrivalTime);
            case ALPHABETICAL:
                return Comparator.comparing(p -> p.getName().toLowerCase());
            default:
                throw new IllegalStateException("Unknown mode: " + this);
        }
    }
}

// ─────────────────────────────────────────────
//  Service: EmergencyRoom
// ─────────────────────────────────────────────
class EmergencyRoom {

    private PriorityQueue<Patient> waitingQueue;
    private final List<Patient>    treatedLog;
    private PriorityMode           mode;
    private final HospitalResources resources;

    public EmergencyRoom(PriorityMode initialMode, HospitalResources resources) {
        this.mode         = initialMode;
        this.waitingQueue = new PriorityQueue<>(initialMode.comparator());
        this.treatedLog   = new ArrayList<>();
        this.resources    = resources;
    }

    // ── Queue operations ──────────────────────

    public boolean admit(Patient patient) {
        if (waitingQueue.size() >= resources.getCapacity()) {
            System.out.println("  🚫 Hospital at capacity! Cannot admit more patients.");
            return false;
        }
        waitingQueue.offer(patient);
        return true;
    }

    public Patient treatNext() {
        if (waitingQueue.isEmpty()) return null;
        Patient next = waitingQueue.poll();
        next.markTreatmentStart();
        next.improveSurvival(resources);
        treatedLog.add(next);
        return next;
    }

    public Patient peekNext() { return waitingQueue.peek(); }
    public int     size()     { return waitingQueue.size(); }
    public boolean isEmpty()  { return waitingQueue.isEmpty(); }

    // ── Priority mode switch ──────────────────

    /**
     * Rebuild the PriorityQueue with the new Comparator.
     * All waiting patients are re-inserted so they appear in the new order.
     */
    public void setPriorityMode(PriorityMode newMode) {
        PriorityQueue<Patient> rebuilt = new PriorityQueue<>(newMode.comparator());
        rebuilt.addAll(waitingQueue);
        this.waitingQueue = rebuilt;
        this.mode         = newMode;
    }

    public PriorityMode getMode() { return mode; }

    // ── Display helpers ───────────────────────

    public void printWaitingList() {
        if (waitingQueue.isEmpty()) {
            System.out.println("  (queue is empty)");
            return;
        }
        // Copy + sort for display (PriorityQueue iterator order is not guaranteed)
        List<Patient> sorted = new ArrayList<>(waitingQueue);
        sorted.sort(mode.comparator());

        System.out.printf("  %-6s %-20s %-24s %s%n", "ID", "Name", "Severity", "Condition");
        System.out.println("  " + "─".repeat(76));
        for (Patient p : sorted) {
            System.out.printf("  [#%-4d] %-20s │ %-22s │ %s%n",
                    p.getId(), p.getName(), p.getSeverity().label, p.getCondition());
        }
    }

    public void printTreatmentLog() {
        if (treatedLog.isEmpty()) {
            System.out.println("  (no patients treated yet)");
            return;
        }
        System.out.printf("  %-4s %-7s %-20s %-24s %-26s %s%n",
                "#", "ID", "Name", "Severity", "Condition", "Wait Time");
        System.out.println("  " + "─".repeat(98));
        for (int i = 0; i < treatedLog.size(); i++) {
            Patient p = treatedLog.get(i);
            System.out.printf("  %-4d[#%-4d] %-20s │ %-22s │ %-24s │ ⏱ %s%n",
                    i + 1, p.getId(), p.getName(),
                    p.getSeverity().label, p.getCondition(), p.formatWaitTime());
        }
    }
}

// ─────────────────────────────────────────────
//  Main: Interactive CLI
// ─────────────────────────────────────────────
public class StormTriage {

    private static final Scanner sc = new Scanner(System.in);

    // ── UI helpers ────────────────────────────

    static void banner(String title) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.printf ("║ %-62s║%n", " " + title);
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    static void section(String title) {
        System.out.println("\n┌─ " + title + " " + "─".repeat(Math.max(0, 58 - title.length())));
    }

    /** Reads a trimmed non-empty line; re-prompts on blank input. */
    static String prompt(String label) {
        while (true) {
            System.out.print("  " + label + ": ");
            String line = sc.nextLine().trim();
            if (!line.isEmpty()) return line;
            System.out.println("  ⚠️  Input cannot be empty. Please try again.");
        }
    }

    /** Reads an integer in [min, max]; re-prompts on invalid input. */
    static int promptInt(String label, int min, int max) {
        while (true) {
            String raw = prompt(label + " [" + min + "-" + max + "]");
            try {
                int v = Integer.parseInt(raw);
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) {}
            System.out.printf("  ⚠️  Please enter a number between %d and %d.%n", min, max);
        }
    }

    // ── Sub-menus ─────────────────────────────

    /** Walk the user through registering a new patient. */
    static void menuAddPatient(EmergencyRoom er) {
        section("Add New Patient");
        String name      = prompt("Patient name");
        String condition = prompt("Condition / chief complaint");

        System.out.println();
        System.out.println("  Select severity level:");
        Patient.Severity[] severities = Patient.Severity.values();
        for (int i = 0; i < severities.length; i++) {
            System.out.printf("    %d) %s%n", i + 1, severities[i].label);
        }
        int choice           = promptInt("Severity", 1, severities.length);
        Patient.Severity sev = severities[choice - 1];

        Patient p = new Patient(name, sev, condition);
        boolean check = er.admit(p);
        if (check) {
            System.out.printf("%n  ✅ %s admitted as [#%d] with priority: %s%n",
                p.getName(), p.getId(), sev.label);
        }
    }

    /** Preview and confirm treatment of the next patient. */
    static void menuTreatNext(EmergencyRoom er) {
        section("Treat Next Patient");
        if (er.isEmpty()) {
            System.out.println("  ⚠️  No patients are currently waiting.");
            return;
        }
        Patient next = er.peekNext();
        System.out.printf("  👁️  Next up : %s%n", next.getName());
        System.out.printf("      Severity: %s%n", next.getSeverity().label);
        System.out.printf("      Condition: %s%n", next.getCondition());
        System.out.print("\n  Confirm treatment? (y/n): ");
        String confirm = sc.nextLine().trim().toLowerCase();
        if (confirm.equals("y") || confirm.equals("yes")) {
            Patient treated = er.treatNext();
            System.out.printf("%n  🏥 Treating: %-20s (%s)  ⏱ waited %s%n",
                    treated.getName(), treated.getSeverity().label, treated.formatWaitTime());
        } else {
            System.out.println("  ↩️  Treatment cancelled.");
        }
    }

    /** Let the user switch the queue's priority ordering. */
    static void menuChangePriority(EmergencyRoom er) {
        section("Change Priority Mode");
        System.out.printf("  Current mode: %s%n%n", er.getMode().description);
        System.out.println("  Select new priority mode:");
        PriorityMode[] modes = PriorityMode.values();
        for (int i = 0; i < modes.length; i++) {
            String marker = (modes[i] == er.getMode()) ? "  ◀ current" : "";
            System.out.printf("    %d) %s%s%n", i + 1, modes[i].description, marker);
        }
        int          choice   = promptInt("Mode", 1, modes.length);
        PriorityMode selected = modes[choice - 1];

        if (selected == er.getMode()) {
            System.out.println("\n  ℹ️  Already using that mode — no change.");
        } else {
            er.setPriorityMode(selected);
            System.out.printf("\n  ✅ Priority mode changed to: %s%n", selected.description);
            if (!er.isEmpty()) {
                System.out.println("  📋 Queue re-ordered under new mode:");
                er.printWaitingList();
            }
        }
    }

    // ── Main menu loop ────────────────────────

    public static void main(String[] args) {

        banner("🏥  Tropical Storm Hospital Triage  —  Interactive CLI");

        // ── Startup: open the ER with SEVERITY mode as default ───────────────
        // Patients are pre-loaded before the user reaches the interactive menu.
        HospitalResources resources = new HospitalResources();
        section("Initializing Hospital Resources");
        resources.printStatus();

        EmergencyRoom er = new EmergencyRoom(PriorityMode.SEVERITY, resources);

        section("Registering Initial Patients");
        System.out.println("  The following patients have already arrived at the ER:\n");

        Patient[] initial = {
                new Patient("Alice Martin",  Patient.Severity.MODERATE, "Broken arm"),
                new Patient("Bob Chen",      Patient.Severity.CRITICAL, "Cardiac arrest"),
                new Patient("Carol Davis",   Patient.Severity.MINOR,    "Mild fever"),
                new Patient("David Kim",     Patient.Severity.URGENT,   "Severe bleeding"),
                new Patient("Eva Rossi",     Patient.Severity.CRITICAL, "Stroke symptoms"),
                new Patient("Frank Obi",     Patient.Severity.MODERATE, "Dislocated knee"),
                new Patient("Grace Patel",   Patient.Severity.URGENT,   "Asthma attack"),
                new Patient("Henry Walsh",   Patient.Severity.MINOR,    "Sprained ankle")
        };

        for (Patient p : initial) {
            boolean check = er.admit(p);
            if (check) {
                System.out.printf("  ✅ Admitted  %-20s  %s%n",
                    p.getName(), p.getSeverity().label);
            }
        }

        System.out.printf("%n  📋 %d patients in the queue. Current priority mode: %s%n",
                er.size(), er.getMode().description);
        System.out.println();
        er.printWaitingList();

        // ── Main loop ─────────────────────────────
        while (true) {
            System.out.println();
            System.out.println("┌──────────────────────────────────────────────────────────┐");
            System.out.printf ("│ Priority Mode :%-42s│%n", er.getMode().description);
            System.out.printf ("│ Patients waiting: %-39d│%n", er.size());
            System.out.println("├──────────────────────────────────────────────────────────┤");
            System.out.println("│  1) Add a new patient                                    │");
            System.out.println("│  2) Treat next patient                                   │");
            System.out.println("│  3) View waiting queue                                   │");
            System.out.println("│  4) View treatment log                                   │");
            System.out.println("│  5) Change priority mode                                 │");
            System.out.println("│  6) Exit                                                 │");
            System.out.println("└──────────────────────────────────────────────────────────┘");

            int choice = promptInt("Choose option", 1, 6);

            switch (choice) {
                case 1: menuAddPatient(er);    break;
                case 2: menuTreatNext(er);     break;
                case 3:
                    section("Waiting Queue  (mode: " + er.getMode().description + ")");
                    er.printWaitingList();
                    break;
                case 4:
                    section("Treatment Log");
                    er.printTreatmentLog();
                    break;
                case 5: menuChangePriority(er); break;
                case 6:
                    section("Session Summary");
                    er.printTreatmentLog();
                    banner("👋  Goodbye — ER session ended.");
                    sc.close();
                    return;
            }
        }
    }
}