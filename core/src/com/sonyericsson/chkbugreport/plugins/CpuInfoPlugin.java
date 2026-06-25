/*
 * Copyright (C) 2024 Sony Mobile Communications AB
 *
 * This file is part of ChkBugReport.
 *
 * ChkBugReport is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * ChkBugReport is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ChkBugReport.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sonyericsson.chkbugreport.plugins;

import com.sonyericsson.chkbugreport.BugReportModule;
import com.sonyericsson.chkbugreport.Module;
import com.sonyericsson.chkbugreport.Plugin;
import com.sonyericsson.chkbugreport.Section;
import com.sonyericsson.chkbugreport.doc.Block;
import com.sonyericsson.chkbugreport.doc.Chapter;
import com.sonyericsson.chkbugreport.doc.Para;
import com.sonyericsson.chkbugreport.doc.Table;

import java.util.Vector;

public class CpuInfoPlugin extends Plugin {

    private static final String TAG = "[CpuInfoPlugin]";

    private boolean mLoaded;
    private CpuSummary mSummary;
    private Vector<CpuEntry> mEntries = new Vector<CpuEntry>();

    @Override
    public int getPrio() {
        return 84;
    }

    @Override
    public void reset() {
        mLoaded = false;
        mSummary = null;
        mEntries.clear();
    }

    @Override
    public void load(Module mod) {
        Section sec = mod.findSection(Section.CPU_INFO);
        if (sec == null) {
            mod.printErr(3, TAG + "Section not found: " + Section.CPU_INFO + " (aborting plugin)");
            return;
        }

        mSummary = new CpuSummary();
        int line = 0, cnt = sec.getLineCount();

        // Scan forward past blank lines, metadata (duration lines, etc.) and summary headers
        while (line < cnt) {
            String buff = sec.getLine(line).trim();
            if (buff.isEmpty() || buff.startsWith("------")) {
                line++;
                continue;
            }
            if (buff.startsWith("Threads:")) {
                parseThreadSummary(buff);
                line++;
            } else if (buff.startsWith("Mem:")) {
                line++;
            } else if (buff.startsWith("Swap:")) {
                line++;
            } else if (buff.startsWith("User ") && buff.contains("%")) {
                // Old format: "User 2%, System 9%, IOW 0%, IRQ 0%"
                parseOldCpuPercent(buff);
                line++;
            } else if (buff.contains("%cpu")) {
                // A14 format: "800%cpu 135%user   9%nice 196%sys 427%idle ..."
                parseA14CpuSummary(buff);
                line++;
            } else if (buff.startsWith("User ") && buff.contains("+")) {
                // Old format: "User 3 + Nice 0 + Sys 10 + Idle 98 ..."
                parseOldCpuTicks(buff);
                line++;
            } else if (buff.startsWith("PID")) {
                break;
            } else {
                break;
            }
        }

        // Find the table header (if not already there)
        while (line < cnt) {
            String buff = sec.getLine(line).trim();
            if (buff.startsWith("PID")) {
                line++;
                break;
            }
            if (!buff.isEmpty()) break;
            line++;
        }

        // Determine format from header line; guard against out-of-bounds
        if (line > 0 && line <= cnt) {
            String prev = sec.getLine(line - 1);
            if (prev.contains("CPU%")) {
                parseOldFormatTable(sec, line, cnt);
            } else {
                parseA14FormatTable(sec, line, cnt);
            }
        }

        mLoaded = true;
    }

    private void parseThreadSummary(String buff) {
        // "Threads: 4074 total,   3 running, 4071 sleeping,   0 stopped,   0 zombie"
        String parts[] = buff.split(",");
        for (String p : parts) {
            p = p.trim();
            if (p.endsWith("total")) {
                mSummary.threadsTotal = parseIntBefore(p, ' ');
            } else if (p.endsWith("running")) {
                mSummary.threadsRunning = parseIntBefore(p, ' ');
            } else if (p.endsWith("sleeping")) {
                mSummary.threadsSleeping = parseIntBefore(p, ' ');
            } else if (p.endsWith("stopped")) {
                mSummary.threadsStopped = parseIntBefore(p, ' ');
            } else if (p.endsWith("zombie")) {
                mSummary.threadsZombie = parseIntBefore(p, ' ');
            }
        }
    }

    private void parseA14CpuSummary(String buff) {
        // "800%cpu 135%user   9%nice 196%sys 427%idle   8%iow  18%irq   8%sirq   0%host"
        String parts[] = buff.trim().split("\\s+");
        for (String p : parts) {
            if (p.endsWith("%cpu")) {
                mSummary.cpuPerc = parseFloatBefore(p, '%');
            } else if (p.endsWith("%user")) {
                mSummary.userPerc = parseFloatBefore(p, '%');
            } else if (p.endsWith("%nice")) {
                mSummary.nicePerc = parseFloatBefore(p, '%');
            } else if (p.endsWith("%sys")) {
                mSummary.sysPerc = parseFloatBefore(p, '%');
            } else if (p.endsWith("%idle")) {
                mSummary.idlePerc = parseFloatBefore(p, '%');
            } else if (p.endsWith("%iow")) {
                mSummary.iowPerc = parseFloatBefore(p, '%');
            } else if (p.endsWith("%irq")) {
                mSummary.irqPerc = parseFloatBefore(p, '%');
            } else if (p.endsWith("%sirq")) {
                mSummary.sirqPerc = parseFloatBefore(p, '%');
            } else if (p.endsWith("%host")) {
                mSummary.hostPerc = parseFloatBefore(p, '%');
            }
        }
        int cores = (int)(mSummary.cpuPerc / 100.0f + 0.5f);
        mSummary.numCores = cores > 0 ? cores : 1;
    }

    private void parseOldCpuPercent(String buff) {
        // "User 2%, System 9%, IOW 0%, IRQ 0%"
        String parts[] = buff.split(",");
        for (String p : parts) {
            p = p.trim();
            if (p.startsWith("User")) {
                mSummary.userPerc = parseFloatBefore(p.substring(p.indexOf(' ') + 1), '%');
            } else if (p.startsWith("System")) {
                mSummary.sysPerc = parseFloatBefore(p.substring(p.indexOf(' ') + 1), '%');
            } else if (p.startsWith("IOW")) {
                mSummary.iowPerc = parseFloatBefore(p.substring(p.indexOf(' ') + 1), '%');
            } else if (p.startsWith("IRQ")) {
                mSummary.irqPerc = parseFloatBefore(p.substring(p.indexOf(' ') + 1), '%');
            }
        }
        mSummary.numCores = 1;
        mSummary.cpuPerc = mSummary.userPerc + mSummary.sysPerc + mSummary.iowPerc + mSummary.irqPerc;
    }

    private void parseOldCpuTicks(String buff) {
        // "User 3 + Nice 0 + Sys 10 + Idle 98 + IOW 0 + IRQ 0 + SIRQ 0 = 111"
        String parts[] = buff.split("\\+");
        for (String p : parts) {
            p = p.trim();
            if (p.startsWith("User ")) {
                mSummary.cpuTicks = parseIntBefore(p.substring(5), ' ');
            } else if (p.startsWith("Nice ")) {
                mSummary.niceTicks = parseIntBefore(p.substring(5), ' ');
            } else if (p.startsWith("Sys ")) {
                mSummary.sysTicks = parseIntBefore(p.substring(4), ' ');
            } else if (p.startsWith("Idle ")) {
                mSummary.idleTicks = parseIntBefore(p.substring(5), ' ');
            } else if (p.startsWith("IOW ")) {
                mSummary.iowTicks = parseIntBefore(p.substring(4), ' ');
            } else if (p.startsWith("IRQ ")) {
                mSummary.irqTicks = parseIntBefore(p.substring(4), ' ');
            } else if (p.startsWith("SIRQ ")) {
                mSummary.sirqTicks = parseIntBefore(p.substring(5), ' ');
            } else if (p.contains("=")) {
                // " = 111" part
            }
        }
    }

    private void parseA14FormatTable(Section sec, int start, int cnt) {
        for (int i = start; i < cnt; i++) {
            String buff = sec.getLine(i);
            if (buff.trim().isEmpty()) break;
            String parts[] = buff.split("\\s+");
            if (parts.length < 11) break;

            CpuEntry e = new CpuEntry();
            e.pid = parseIntSafe(parts[0]);
            e.tid = parseIntSafe(parts[1]);
            e.user = parts[2];
            e.pr = parseIntSafe(parts[3]);
            e.ni = parseIntSafe(parts[4]);
            e.cpuPerc = parseFloatSafe(parts[5]);
            e.status = parts[6];
            e.virt = parts[7];
            e.res = parts[8];
            e.pcy = parts[9];
            // parts[10] = CMD (command, may be truncated)
            // remaining = NAME (full thread/process name)
            StringBuilder name = new StringBuilder();
            for (int j = 11; j < parts.length; j++) {
                if (name.length() > 0) name.append(" ");
                name.append(parts[j]);
            }
            e.name = name.toString();

            mEntries.add(e);
        }
    }

    private void parseOldFormatTable(Section sec, int start, int cnt) {
        for (int i = start; i < cnt; i++) {
            String buff = sec.getLine(i);
            if (buff.trim().isEmpty()) break;
            String parts[] = buff.split("\\s+");
            if (parts.length < 9) break;

            CpuEntry e = new CpuEntry();
            e.pid = parseIntSafe(parts[0]);
            e.tid = parseIntSafe(parts[1]);
            String cpuVal = parts[2];
            if (cpuVal.endsWith("%")) {
                e.cpuPerc = parseFloatSafe(cpuVal.substring(0, cpuVal.length() - 1));
            }
            e.status = parts[3];
            e.virt = parts[4];
            e.res = parts[5];
            e.pcy = parts[6];
            e.user = parts[7];
            StringBuilder name = new StringBuilder();
            for (int j = 8; j < parts.length; j++) {
                if (name.length() > 0) name.append(" ");
                name.append(parts[j]);
            }
            e.name = name.toString();

            mEntries.add(e);
        }
    }

    private int parseIntBefore(String s, char delim) {
        int idx = s.indexOf(delim);
        if (idx < 0) return 0;
        try {
            return Integer.parseInt(s.substring(0, idx).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private float parseFloatBefore(String s, char delim) {
        int idx = s.indexOf(delim);
        if (idx < 0) return 0;
        try {
            return Float.parseFloat(s.substring(0, idx).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private float parseFloatSafe(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void generate(Module mod) {
        if (!mLoaded) return;
        BugReportModule br = (BugReportModule) mod;

        Chapter ch = br.findOrCreateChapter("CPU/Usage");
        generateSummary(ch);
        generateTable(br, ch);
    }

    private void generateSummary(Chapter ch) {
        if (mSummary == null) return;

        Block summary = new Block(ch);
        summary.addStyle("code");
        if (mSummary.threadsTotal > 0) {
            summary.add("Threads: " + mSummary.threadsTotal + " total, "
                + mSummary.threadsRunning + " running, "
                + mSummary.threadsSleeping + " sleeping, "
                + mSummary.threadsStopped + " stopped, "
                + mSummary.threadsZombie + " zombie");
        }
        if (mSummary.numCores > 0) {
            StringBuffer sb = new StringBuffer();
            sb.append("CPU (").append(mSummary.numCores).append(" cores): ");
            sb.append(String.format("%.1f%% total", mSummary.cpuPerc));
            if (mSummary.numCores > 1) {
                sb.append(" (").append(String.format("%.1f%%", mSummary.cpuPerc / mSummary.numCores)).append(" avg per core)");
            }
            sb.append(" — user: ").append(String.format("%.1f%%", mSummary.userPerc));
            sb.append(" nice: ").append(String.format("%.1f%%", mSummary.nicePerc));
            sb.append(" sys: ").append(String.format("%.1f%%", mSummary.sysPerc));
            sb.append(" idle: ").append(String.format("%.1f%%", mSummary.idlePerc));
            sb.append(" iow: ").append(String.format("%.1f%%", mSummary.iowPerc));
            sb.append(" irq: ").append(String.format("%.1f%%", mSummary.irqPerc));
            if (mSummary.sirqPerc > 0) {
                sb.append(" sirq: ").append(String.format("%.1f%%", mSummary.sirqPerc));
            }
            if (mSummary.hostPerc > 0) {
                sb.append(" host: ").append(String.format("%.1f%%", mSummary.hostPerc));
            }
            new Para(ch).add(sb.toString());

            // Show per-core average breakdown
            if (mSummary.numCores > 1) {
                Block breakdown = new Block(ch);
                breakdown.addStyle("code");
                float divisor = mSummary.numCores;
                breakdown.add("Per-core average: "
                    + "user " + String.format("%.1f%%", mSummary.userPerc / divisor)
                    + " | nice " + String.format("%.1f%%", mSummary.nicePerc / divisor)
                    + " | sys " + String.format("%.1f%%", mSummary.sysPerc / divisor)
                    + " | idle " + String.format("%.1f%%", mSummary.idlePerc / divisor)
                    + " | iow " + String.format("%.1f%%", mSummary.iowPerc / divisor)
                    + " | irq " + String.format("%.1f%%", mSummary.irqPerc / divisor));
            }
        }
        if (mSummary.cpuTicks > 0 || mSummary.sysTicks > 0) {
            Block ticks = new Block(ch);
            ticks.addStyle("code");
            int total = mSummary.cpuTicks + mSummary.niceTicks + mSummary.sysTicks
                + mSummary.idleTicks + mSummary.iowTicks + mSummary.irqTicks + mSummary.sirqTicks;
            ticks.add("Ticks: user=" + mSummary.cpuTicks + " nice=" + mSummary.niceTicks
                + " sys=" + mSummary.sysTicks + " idle=" + mSummary.idleTicks
                + " iow=" + mSummary.iowTicks + " irq=" + mSummary.irqTicks
                + " sirq=" + mSummary.sirqTicks + " total=" + total);
        }
    }

    private void generateTable(BugReportModule br, Chapter ch) {
        if (mEntries.size() == 0) return;

        new Para(ch).add("Per-process/thread CPU usage:");
        Table t = new Table(Table.FLAG_SORT, ch);
        t.setCSVOutput(br, "cpu_usage");
        t.setTableName(br, "cpu_usage");
        t.addColumn("PID", Table.FLAG_ALIGN_RIGHT, "pid int");
        t.addColumn("TID", Table.FLAG_ALIGN_RIGHT, "tid int");
        t.addColumn("User", Table.FLAG_NONE, "user varchar");
        t.addColumn("CPU%", Table.FLAG_ALIGN_RIGHT, "cpu_p float");
        t.addColumn("Status", Table.FLAG_NONE, "status varchar");
        t.addColumn("VIRT", Table.FLAG_ALIGN_RIGHT, "virt varchar");
        t.addColumn("RES", Table.FLAG_ALIGN_RIGHT, "res varchar");
        t.addColumn("PCY", Table.FLAG_NONE, "pcy varchar");
        t.addColumn("Name", Table.FLAG_NONE, "name varchar");
        t.begin();

        for (CpuEntry e : mEntries) {
            t.addData(e.pid);
            t.addData(e.tid);
            t.addData(e.user);
            t.addData(String.format("%.1f", e.cpuPerc));
            t.addData(e.status);
            t.addData(e.virt);
            t.addData(e.res);
            t.addData(e.pcy);
            t.addData(e.name);
        }
        t.end();
    }

    static class CpuSummary {
        int threadsTotal, threadsRunning, threadsSleeping, threadsStopped, threadsZombie;
        int numCores = 1;
        float cpuPerc, userPerc, nicePerc, sysPerc, idlePerc, iowPerc, irqPerc, sirqPerc, hostPerc;
        int cpuTicks, niceTicks, sysTicks, idleTicks, iowTicks, irqTicks, sirqTicks;
    }

    static class CpuEntry {
        int pid, tid;
        String user;
        int pr, ni;
        float cpuPerc;
        String status;
        String virt, res;
        String pcy;
        String name;
    }

}
