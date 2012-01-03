package org.mskcc.cgds.scripts;

import org.mskcc.cgds.dao.DaoException;
import org.mskcc.cgds.dao.DaoGeneOptimized;
import org.mskcc.cgds.dao.MySQLbulkLoader;
import org.mskcc.cgds.dao.DaoSangerCensus;
import org.mskcc.cgds.model.CanonicalGene;
import org.mskcc.cgds.util.ConsoleUtil;
import org.mskcc.cgds.util.FileUtil;
import org.mskcc.cgds.util.ProgressMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Command Line Tool to Import Sanger Cancer Gene Census Data.
 */
public class ImportSangerCensusData {
    private ProgressMonitor pMonitor;
    private File censusFile;

    public ImportSangerCensusData(File censusFile, ProgressMonitor pMonitor) {
        this.censusFile = censusFile;
        this.pMonitor = pMonitor;
    }

    public void importData() throws IOException, DaoException {
        MySQLbulkLoader.bulkLoadOn();
        DaoSangerCensus daoCensus = DaoSangerCensus.getInstance();
        daoCensus.deleteAllRecords();
        FileReader reader = new FileReader(censusFile);
        BufferedReader buf = new BufferedReader(reader);
        buf.readLine();   //  Skip Header Line
        String line = buf.readLine();
        DaoGeneOptimized daoGene = DaoGeneOptimized.getInstance();
        while (line != null) {
            if (pMonitor != null) {
                pMonitor.incrementCurValue();
                ConsoleUtil.showProgress(pMonitor);
            }
            String parts[] = line.split("\t");
            long entrezGeneId = Long.parseLong(parts[2]);
            CanonicalGene gene = daoGene.getGene(entrezGeneId);
            if (gene != null) {
                boolean cancerSomaticMutation = getBoolean(parts, 5);
                boolean cancerGermlineMutation = getBoolean(parts, 6);
                String tumorTypesSomaticMutation = getColumn(parts, 7);
                String tumorTypesGermlineMutation = getColumn(parts, 8);
                String cancerSyndrome = getColumn(parts, 9);
                String tissueType = getColumn(parts, 10);
                String mutationType = getColumn (parts, 12);
                String translocationPartner = getColumn (parts, 13);
                boolean otherGermlineMutation = getBoolean(parts, 14);
                String otherDisease = getColumn(parts, 15);
                daoCensus.addGene(gene, cancerSomaticMutation, cancerGermlineMutation,
                        tumorTypesSomaticMutation, tumorTypesGermlineMutation, cancerSyndrome,
                        tissueType, mutationType, translocationPartner, otherGermlineMutation,
                        otherDisease);
            } else {
                pMonitor.setCurrentMessage("Cannot identify gene:  " + entrezGeneId);
            }
            line = buf.readLine();
        }
    }

    private String getColumn (String parts[], int index) {
        try {
            String part = parts[index];
            return part.replaceAll("\"", "");
        } catch (ArrayIndexOutOfBoundsException e) {
            return "";
        }
    }

    private boolean getBoolean (String parts[], int index) {
        String value = getColumn(parts, index);
        if (value != null && value.equalsIgnoreCase("yes")) {
            return true;
        } else {
            return false;
        }
    }


    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("command line usage:  importSangerCensus.pl <sanger.txt>");
            System.exit(1);
        }
        ProgressMonitor pMonitor = new ProgressMonitor();
        pMonitor.setConsoleMode(true);

        File geneFile = new File(args[0]);
        System.out.println("Reading data from:  " + geneFile.getAbsolutePath());
        int numLines = FileUtil.getNumLines(geneFile);
        System.out.println(" --> total number of lines:  " + numLines);
        pMonitor.setMaxValue(numLines);
        ImportSangerCensusData parser = new ImportSangerCensusData(geneFile, pMonitor);
        parser.importData();
        ConsoleUtil.showWarnings(pMonitor);
        System.err.println("Done.");
    }
}