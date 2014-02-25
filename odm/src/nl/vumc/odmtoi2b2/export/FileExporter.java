/**
 * Copyright(c) 2014 VU University Medical Center.
 * Licensed under the Apache License version 2.0 (see http://opensource.org/licenses/Apache-2.0).
 */

package nl.vumc.odmtoi2b2.export;

import com.google.common.base.Joiner;
import com.recomdata.i2b2.entity.I2B2ClinicalDataInfo;
import com.recomdata.i2b2.entity.I2B2StudyInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For each study, there is one FileExporter object. This class supports exporting ODM data to four files in i2b2
 * format:
 * 1) the concept map file,
 * 2) the columns file,
 * 3) the word map file, and
 * 4) the clinical data file.
 *
 * @author <a href="mailto:w.blonde@vumc.nl">Ward Blondé</a>
 * @author <a href="mailto:f.debruijn@vumc.nl">Freek de Bruijn</a>
 */
public class FileExporter {
    /**
     * Logger for this class.
     */
    private static final Log logger = LogFactory.getLog(FileExporter.class);

    /**
     * The column identifier of the very first column, which contains the subject identifiers.
     */
    private static final String FIRST_COLUMN_ID_WITH_SUBJECT_IDS = "firstColumnIdWithSubjectIds";

    /**
     * The directory where the export files will be written to.
     */
    private final String exportFilePath;

    /**
     * The name of the study.
     */
    private final String studyName;

    /**
     * The name of the concept map file.
     */
    private String conceptMapFileName;

    /**
     * Whether the line with the concept map headers still has to be written to file.
     */
    private boolean writeConceptMapHeaders;

    /**
     * The writer for writing the concept map file.
     */
    private BufferedWriter conceptMapWriter;

    /**
     * The name of the columns file.
     */
    private String columnsFileName;

    /**
     * The writer for writing the columns file.
     */
    private BufferedWriter columnsWriter;

    /**
     * Is set to true right after the column number was increased.
     */
    private boolean increasedColumnNumber;

    /**
     * The name of the word map file.
     */
    private String wordMapFileName;

    /**
     * Whether the line with the word map headers still has to be written to file.
     */
    private boolean writeWordMapHeaders;

    /**
     * The writer for writing the word map file.
     */
    private BufferedWriter wordMapWriter;

    /**
     * The value that is written to the clinical data file, instead of the words in the word map file,
     * which maps these values with words.
     */
    private int valueCounter;

    /**
     * The name of the clinical data file.
     */
    private String clinicalDataFileName;

    /**
     * Whether the line with the clinical data headers still has to be written to file.
     */
    private boolean writeClinicalDataHeaders;

    /**
     * The writer for exporting the clinical data file.
     */
    private BufferedWriter clinicalDataWriter;

    /**
     * The column headers for the clinical data.
     */
    private List<String> columnHeaders;

    /**
     * The column IDs (paths) for the clinical data.
     */
    private List<String> columnIds;

    /**
     * The patient IDs (SubjectKeys), which correspond to the rows in the clinical data.
     */
    private List<String> patientIds;

    /**
     * The patient number that clinical data info records are being processed for. All data for a patient is collected
     * and written on one line.
     */
    private String currentPatientNumber;

    /**
     * The current column number during the processing of the study info.
     */
    private int currentColumnNumber;

    /**
     * The current column id during the processing of the study info.
     */
    private String currentColumnId;

    /**
     * Mapping of (column ID + word) to values for the current patient.
     */
    private Map<String, String> wordMap;

    /**
     * A map of maps: Map<patientID, patientData>, with patientData a map of columnIds to data values.
     */
    private Map<String, Map<String, String>> clinicalDataMap;

    /**
     * Construct a file exporter.
     *
     * @param exportFilePath the directory for the export files.
     * @param studyName      the name of the study.
     * @throws IOException when creating the file fails.
     */
    public FileExporter(final String exportFilePath, final String studyName) throws IOException {
        final String studyNameWithUnderscores = studyName.replace(' ', '_');
        this.exportFilePath = exportFilePath;
        this.studyName = studyNameWithUnderscores;
        this.conceptMapFileName = studyNameWithUnderscores + "_concept_map.txt";
        this.columnsFileName = studyNameWithUnderscores + "_columns.txt";
        this.wordMapFileName = studyNameWithUnderscores + "_word_map.txt";
        this.clinicalDataFileName = studyNameWithUnderscores + "_clinical_data.txt";
        setConceptMapName(this.conceptMapFileName);
        setColumnsName(this.columnsFileName);
        setWordMapName(this.wordMapFileName);
        setClinicalDataName(this.clinicalDataFileName);
        this.writeConceptMapHeaders = true;
        this.writeWordMapHeaders = true;
        this.valueCounter = 1;
        this.writeClinicalDataHeaders = true;
        this.increasedColumnNumber = false;
        this.currentColumnNumber = 1;
        this.currentColumnId = null;
        this.columnHeaders = new ArrayList<>();
        columnHeaders.add(studyName + "_SUBJ_ID");
        this.columnIds = new ArrayList<>();
        columnIds.add(FIRST_COLUMN_ID_WITH_SUBJECT_IDS);
        this.patientIds = new ArrayList<>();
        this.wordMap = new HashMap<>();
        this.clinicalDataMap = new HashMap<>();
    }

    /**
     * Set the output filename for the columns metadata file.
     *
     * @param columnsFileName the output filename.
     */
    private void setColumnsName(final String columnsFileName) {
        try {
            columnsWriter = new BufferedWriter(new FileWriter(exportFilePath + columnsFileName));
            logger.info("Writing columns to file " + exportFilePath + columnsFileName);
        } catch (final IOException e) {
            logger.error("Error while setting the columns filename.", e);
        }
    }

    /**
     * Set the output filename for the wordmap metadata file.
     *
     * @param wordMapFileName the output filename.
     */
    private void setWordMapName(final String wordMapFileName) {
        try {
            wordMapWriter = new BufferedWriter(new FileWriter(exportFilePath + wordMapFileName));
            logger.info("Writing word mappings to file " + exportFilePath + wordMapFileName);
        } catch (final IOException e) {
            logger.error("Error while setting the wordmap filename.", e);
        }
    }

    /**
     * Set the output filename for the conceptmap metadata file.
     *
     * @param conceptMapFileName the output filename.
     */
    private void setConceptMapName(final String conceptMapFileName) {
        try {
            conceptMapWriter = new BufferedWriter(new FileWriter(exportFilePath + conceptMapFileName));
            logger.info("Writing concept map to file " + exportFilePath + conceptMapFileName);
        } catch (final IOException e) {
            logger.error("Error while setting the conceptmap filename.", e);
        }
    }

    /**
     * Set the output filename for the clinical data file.
     *
     * @param clinicalDataFileName the output filename.
     */
    private void setClinicalDataName(final String clinicalDataFileName) {
        try {
            clinicalDataWriter = new BufferedWriter(new FileWriter(exportFilePath + clinicalDataFileName));
            logger.info("Writing clinical data to file " + exportFilePath + clinicalDataFileName);
        } catch (final IOException e) {
            logger.error("Error while setting the clinical data filename.", e);
        }
    }

    /**
     * Write the concept mapping in two columns. The first column represents the tree structure as it will
     * appear in i2b2, the second column is the original tree structure from the ODM. Concepts are separated
     * by + symbols.
     *
     * @param studyInfo the metadata study information
     */
    public void writeExportConceptMap(final I2B2StudyInfo studyInfo) {
        if (writeConceptMapHeaders) {
            writeLine(conceptMapWriter, "tranSMART_path\tEDC_path\tControl Vocab Cd");
            writeConceptMapHeaders = false;
        }

        final String path = String.format("%s+%s\t", studyInfo.getNamePath(), studyInfo.getPreferredName());
        writeLine(conceptMapWriter, path + path);
        columnHeaders.add(studyName + "_" + studyInfo.getPreferredName());
        columnIds.add(studyInfo.getCfullname());
    }

    /**
     * Write the columns file: first the clinical data file name, then the path as specified in the second column of the
     * user's input concept map without the last node, then the column number and then the last node of the path.
     * todo: update comment above (user's input concept map: in manual mode; empty columns at the end)
     *
     * @param studyInfo the metadata study information
     */
    public void writeExportColumns(final I2B2StudyInfo studyInfo) {
        if (currentColumnNumber == 1) {
            writeLine(columnsWriter, "Filename\tCategory Code\tColumn Number\tData Label\tData Label Source\t"
                    + "Control Vocab Cd");
            // This first data line is required by tranSMART.
            writeLine(columnsWriter, clinicalDataFileName + "\t\t1\tSUBJ_ID\t\t");
        }
        currentColumnNumber++;
        increasedColumnNumber = true;
        writeLine(columnsWriter, clinicalDataFileName + "\t" + studyInfo.getNamePath() + "\t"
                + currentColumnNumber + "\t" + studyInfo.getPreferredName() + "\t\t");
        currentColumnId = studyInfo.getCfullname();
    }

    /**
     * Write the word mapping file: first the clinical data file name, then the column number, then the data value,
     * and then the mapped word.
     *
     * @param studyInfo the metadata study information
     */

    public void writeExportWordMap(final I2B2StudyInfo studyInfo) {
        if (writeWordMapHeaders) {
            writeLine(wordMapWriter, "Filename\tColumn Number\tOriginal Data Value\tNew Data Values");
            writeWordMapHeaders = false;
        }
        if (increasedColumnNumber) {
            valueCounter = 1;
            increasedColumnNumber = false;
        } else {
            valueCounter++;
        }
        final String value = String.valueOf(valueCounter);
        wordMap.put(currentColumnId + studyInfo.getCname(), value);
        writeLine(wordMapWriter, clinicalDataFileName + "\t" + currentColumnNumber + "\t" + valueCounter + "\t" + studyInfo.getCname());
    }

    /**
     * Write the clinical data to a tab-delimited text file.
     *
     * @param clinicalDataInfo the clinical data to be written to the file
     */
    public void writeExportClinicalDataInfo(final I2B2ClinicalDataInfo clinicalDataInfo) {
        final String columnId = clinicalDataInfo.getConceptCd();

        /**
         * Mapping of column ID to values for the current patient.
         */
        Map<String, String> patientData = new HashMap<>();

        String wordValue = clinicalDataInfo.getTvalChar();
        if (clinicalDataInfo.getNvalNum() != null) {
            wordValue = clinicalDataInfo.getNvalNum().toString();
        }
        currentPatientNumber = clinicalDataInfo.getPatientNum();

        if (clinicalDataMap.containsKey(currentPatientNumber)) {
            patientData = clinicalDataMap.get(currentPatientNumber);
        } else {
            patientIds.add(currentPatientNumber);
            //TODO: problem when currentPatientNumber is longer than 20:
            patientData.put(FIRST_COLUMN_ID_WITH_SUBJECT_IDS, currentPatientNumber);
            clinicalDataMap.put(currentPatientNumber, patientData);
        }

        if (wordMap.get(columnId + wordValue) != null) {
            //fills clinical data with words from wordmap
            patientData.put(columnId, wordMap.get(columnId + wordValue));
        } else {
            patientData.put(columnId, wordValue);
        }

        clinicalDataMap.put(currentPatientNumber, patientData);

        final String className = clinicalDataInfo.getClass().getName();
        logger.trace("[I2B2ODMStudyHandler] " + className.substring(className.lastIndexOf('.') + 1) + ":");
        logger.trace("+ PatientNum = " + clinicalDataInfo.getPatientNum());
        logger.trace("+ ConceptCd  = " + clinicalDataInfo.getConceptCd());
        logger.trace("+ TValChar   = " + clinicalDataInfo.getTvalChar());
        logger.trace("");
    }

    public void writePatientData() {
        writeLine(clinicalDataWriter, Joiner.on('\t').join(columnHeaders));
        for (final String patientId : patientIds) {
            final StringBuilder line = new StringBuilder();
            Map<String, String> patientData = clinicalDataMap.get(patientId);
            for (final String columnId : columnIds) {
                if (line.length() > 0)
                    line.append("\t");
                if (patientData.containsKey(columnId))
                    line.append(patientData.get(columnId));
            }
            writeLine(clinicalDataWriter, line.toString());
        }
    }

    /**
     * Write a line to the export file.
     *
     * @param writer the writer to write to.
     * @param line   the line to be written.
     */
    private void writeLine(BufferedWriter writer, String line) {
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Close the export files.
     */
    public void close() {
        try {
            writePatientData();
            columnsWriter.close();
            wordMapWriter.close();
            conceptMapWriter.close();
            clinicalDataWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * For debugging: write all the data fields that would be written to the database in text-form.
     *
     * @param dataObject the data object that is prepared for loading to the database
     */
    @SuppressWarnings("UnusedDeclaration")
    public void writeExportDataObject(Object dataObject) {
        String className = dataObject.getClass().getName();
        logger.info("[I2B2ODMStudyHandler] " + className.substring(className.lastIndexOf('.') + 1) + ":");
        try {
            for (Field field : dataObject.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                logger.info("- " + field.getName() + ": " + field.get(dataObject));
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        logger.info("");
    }
}
