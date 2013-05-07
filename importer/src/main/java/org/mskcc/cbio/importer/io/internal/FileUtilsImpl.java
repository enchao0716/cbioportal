/** Copyright (c) 2012 Memorial Sloan-Kettering Cancer Center.
**
** This library is free software; you can redistribute it and/or modify it
** under the terms of the GNU Lesser General Public License as published
** by the Free Software Foundation; either version 2.1 of the License, or
** any later version.
**
** This library is distributed in the hope that it will be useful, but
** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
** documentation provided hereunder is on an "as is" basis, and
** Memorial Sloan-Kettering Cancer Center 
** has no obligations to provide maintenance, support,
** updates, enhancements or modifications.  In no event shall
** Memorial Sloan-Kettering Cancer Center
** be liable to any party for direct, indirect, special,
** incidental or consequential damages, including lost profits, arising
** out of the use of this software and its documentation, even if
** Memorial Sloan-Kettering Cancer Center 
** has been advised of the possibility of such damage.  See
** the GNU Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public License
** along with this library; if not, write to the Free Software Foundation,
** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
**/

// package
package org.mskcc.cbio.importer.io.internal;

// imports
import org.mskcc.cbio.importer.Config;
import org.mskcc.cbio.importer.CaseIDs;
import org.mskcc.cbio.importer.FileUtils;
import org.mskcc.cbio.importer.Converter;
import org.mskcc.cbio.importer.model.ImportDataRecord;
import org.mskcc.cbio.importer.model.PortalMetadata;
import org.mskcc.cbio.importer.model.DataMatrix;
import org.mskcc.cbio.importer.model.DatatypeMetadata;
import org.mskcc.cbio.importer.model.CaseListMetadata;
import org.mskcc.cbio.cgds.scripts.NormalizeExpressionLevels;
import org.mskcc.cbio.importer.model.CancerStudyMetadata;
import org.mskcc.cbio.importer.model.DataSourcesMetadata;
import org.mskcc.cbio.importer.util.MetadataUtils;
import org.mskcc.cbio.importer.util.Shell;
import org.mskcc.cbio.importer.converter.internal.MethylationConverterImpl;

import org.mskcc.cbio.liftover.Hg18ToHg19;
import org.mskcc.cbio.oncotator.OncotateTool;
import org.mskcc.cbio.mutassessor.MutationAssessorTool;

import org.apache.commons.io.*;
import org.apache.commons.codec.digest.DigestUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;

import java.lang.reflect.Constructor;

import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Class which implements the FileUtils interface.
 */
class FileUtilsImpl implements org.mskcc.cbio.importer.FileUtils {

    // used in unzip method
    private static int BUFFER = 2048;

	// our logger
	private static Log LOG = LogFactory.getLog(FileUtilsImpl.class);

	// ref to config
	private Config config;

	// location of lift over binary
	private String liftoverBinary;
	@Value("${liftover_binary}")
	public void setLiftOverBinary(String property) { this.liftoverBinary = property; }
	public String getLiftOverBinary() { return MetadataUtils.getCanonicalPath(liftoverBinary); }

	// location of lift over chain
	private String liftoverChain;
	@Value("${liftover_chain_file}")
	public void setLiftOverChain(String property) { this.liftoverChain = property; }
	public String getLiftOverChain() { return MetadataUtils.getCanonicalPath(liftoverChain); }

	/**
	 * Constructor.
     *
     * @param config Config
	 */
	public FileUtilsImpl(Config config) {

		// set members
		this.config = config;
	}

	/**
	 * Computes the MD5 digest for the given file.
	 * Returns the 32 digit hexadecimal.
	 *
	 * @param filename String
	 * @return String
	 * @throws Exception
	 */
	@Override
	public String getMD5Digest(File file) throws Exception {

		if (LOG.isInfoEnabled()) {
			LOG.info("getMD5Digest(): " + file.getCanonicalPath());
		}

        String toReturn = "";
        InputStream is = org.apache.commons.io.FileUtils.openInputStream(file);
        try {
            toReturn = DigestUtils.md5Hex(is);
        }
        finally {
            IOUtils.closeQuietly(is);
        }

        // outta here
        return toReturn;
	}

	/**
	 * Reads the precomputed md5 digest out of a .md5 file (firehose).
     * Assume the file only contains one line wit checksum.
	 *
	 * @param file File
	 * @return String
	 * @throws Exception 
	 */
	@Override
	public String getPrecomputedMD5Digest(File file) throws Exception {

		if (LOG.isInfoEnabled()) {
			LOG.info("getPrecomputedMD5Digest(): " + file.getCanonicalPath());
		}

		String toReturn = "";
        LineIterator it = org.apache.commons.io.FileUtils.lineIterator(file);
        try {
            while (it.hasNext()) {
                String content = it.nextLine();
                if (content.split(" ").length == 2) {
                    toReturn = content.split(" ")[0].toUpperCase();
                }   
            }
        }
        finally {
            LineIterator.closeQuietly(it);
        }

		// outta here
		return toReturn;
    }

    /**
     * Makes a directory, including parent directories if necessary.
     *
     * @param directory File
	 * @throws Exception
     */
    @Override
    public void makeDirectory(File directory) throws Exception {
        
        org.apache.commons.io.FileUtils.forceMkdir(directory);
    }

    /**
     * Deletes a directory recursively.
     *
     * @param directory File
	 * @throws Exception
     */
    @Override
    public void deleteDirectory(File directory) throws Exception {

        org.apache.commons.io.FileUtils.deleteDirectory(directory);
    }

    /**
     * Lists all files in a given directory and its subdirectories.
     *
     * @param directory File
     * @param extensions String[]
     * @param recursize boolean
     * @return Collection<File>
	 * @throws Exception
     */
    @Override
    public Collection<File> listFiles(File directory, String[] extensions, boolean recursive) throws Exception {

        return org.apache.commons.io.FileUtils.listFiles(directory, extensions, recursive);
    }

	/**
	 * Returns the contents of the datafile as specified by ImportDataRecord
     * in an DataMatrix.  May return null if there is a problem reading the file.
	 *
	 * methylationCorrelation matrix is set when we are processing a methlation file.
	 * These files can be extremely large, so the correlation file is used to skip
	 * all rows in the methylation file that do not have a corresponding row in the correlate file.
	 *
	 * @param importDataRecord ImportDataRecord
	 * @param methylationCorrelation DataMatrix
	 * @return DataMatrix
	 * @throws Exception
	 */
    @Override
	public DataMatrix getFileContents(ImportDataRecord importDataRecord, DataMatrix methylationCorrelation) throws Exception {

		if (LOG.isInfoEnabled()) {
			LOG.info("getFileContents(): " + importDataRecord);
		}

        // determine path to file (does override file exist?)
        String fileCanonicalPath = importDataRecord.getCanonicalPathToData();

        // get filedata inputstream
        InputStream fileContents;

        // data can be compressed
		if (GzipUtils.isCompressedFilename(fileCanonicalPath.toLowerCase())) {
            if (LOG.isInfoEnabled()) {
                LOG.info("getFileContents(): processing file: " + fileCanonicalPath);
            }
            fileContents = readContent(importDataRecord,
                                       org.apache.commons.io.FileUtils.openInputStream(new File(fileCanonicalPath)));
        }
        else {
            if (LOG.isInfoEnabled()) {
                LOG.info("getFileContents(): processing file: " + fileCanonicalPath);
            }
            fileContents = org.apache.commons.io.FileUtils.openInputStream(new File(fileCanonicalPath));
        }

        // outta here
        return getDataMatrix(fileContents, methylationCorrelation);
    }

	/**
	 * Get the case list from the staging file.
	 *
	 * @param caseIDs CaseIDs;
     * @param portalMetadata PortalMetadata
	 * @param cancerStudyMetadata CancerStudyMetadata
	 * @param stagingFilename String
	 * @return List<String>
	 * @throws Exception
	 */
	@Override
	public List<String> getCaseListFromStagingFile(CaseIDs caseIDs, PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata, String stagingFilename) throws Exception {

		if (LOG.isInfoEnabled()) {
			LOG.info("getCaseListFromStagingFile(): " + stagingFilename);
		}

		// we use set here
		HashSet<String> caseSet = new HashSet<String>();

		// staging file
		File stagingFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																   cancerStudyMetadata.getStudyPath(),
																   stagingFilename);
		// sanity check
		if (!stagingFile.exists()) {
			return new ArrayList<String>();
		}

		// iterate over all rows in file
		org.apache.commons.io.LineIterator it = org.apache.commons.io.FileUtils.lineIterator(stagingFile);
		try {
			int mafCaseIDColumnIndex = 0;
			boolean processHeader = true;
			while (it.hasNext()) {
				// create a string list from row in file
				List<String> thisRow = Arrays.asList(it.nextLine().split(Converter.VALUE_DELIMITER));
				// is this the header file?
				if (processHeader) {
					// look for MAF file case id column header
					mafCaseIDColumnIndex = thisRow.indexOf(Converter.MUTATION_CASE_ID_COLUMN_HEADER);
					// this is not a MAF file, header contains the case ids, return here
					if (mafCaseIDColumnIndex  == -1) {
						for (String potentialCaseID : thisRow) {
							if (caseIDs.isTumorCaseID(potentialCaseID)) {
								caseSet.add(caseIDs.convertCaseID(potentialCaseID));
							}
						}
						break;
					}
					processHeader = false;
					continue;
				}
				// we want to add the value at mafCaseIDColumnIndex into return set - this is a case ID
				String potentialCaseID = thisRow.get(mafCaseIDColumnIndex);
				if (caseIDs.isTumorCaseID(potentialCaseID)) {
					caseSet.add(caseIDs.convertCaseID(potentialCaseID));
				}
			}
		} finally {
			it.close();
		}

		// outta here
		return new ArrayList<String>(caseSet);
	}

	/**
	 * Creates a temporary file with the given contents.
	 *
	 * @param filename String
	 * @param fileContent String
	 * @return File
	 * @throws Exception
	 */
	@Override
	public File createTmpFileWithContents(String filename, String fileContent) throws Exception {

		File tmpFile = org.apache.commons.io.FileUtils.getFile(org.apache.commons.io.FileUtils.getTempDirectoryPath(),
															   filename);
		org.apache.commons.io.FileUtils.writeStringToFile(tmpFile, fileContent, false);
		return tmpFile;
	}

	/**
	 * Creates (or overwrites) the given file with the given contents. Filename
	 * is canonical path/filename.
	 *
	 * @param filename String
	 * @param fileContent String
	 * @return File
	 * @throws Exception
	 */
	@Override
	public File createFileWithContents(String filename, String fileContent) throws Exception {

		File file = org.apache.commons.io.FileUtils.getFile(filename);
		org.apache.commons.io.FileUtils.writeStringToFile(file, fileContent, false);

		// outta here
		return file;
	}

	/**
	 * Downloads the given file specified via url to the given canonicalDestination.
	 *
	 * @param urlSource String
	 * @param urlDestination String
	 * @throws Exception
	 */
	@Override
	public void downloadFile(String urlSource, String urlDestination) throws Exception {

		// sanity check
		if (urlSource == null || urlSource.length() == 0 ||
			urlDestination == null || urlDestination.length() == 0) {
			throw new IllegalArgumentException("downloadFile(): urlSource or urlDestination argument is null...");
		}

		// URLs for given parameters
		URL source = new URL(urlSource);
		URL destination = new URL(urlDestination);

		// we have a compressed file
		if (GzipUtils.isCompressedFilename(urlSource)) {
			// downlod to temp destination
			File tempDestinationFile = org.apache.commons.io.FileUtils.getFile(org.apache.commons.io.FileUtils.getTempDirectory(),
																			   ""+System.currentTimeMillis()+"."+new File(source.getFile()).getName());
			if (LOG.isInfoEnabled()) {
				LOG.info("downloadFile(), " + urlSource + ", this may take a while...");
			}
			org.apache.commons.io.FileUtils.copyURLToFile(source, tempDestinationFile);
			if (LOG.isInfoEnabled()) {
				LOG.info("downloadFile(), gunzip: we have compressed file, decompressing...");
			}
			// decompress the file
			gunzip(tempDestinationFile.getCanonicalPath());
			if (LOG.isInfoEnabled()) {
				LOG.info("downloadFile(), gunzip complete...");
			}
			// move temp/decompressed file to final destination
			File destinationFile = new File(destination.getFile());
			if (destinationFile.exists()) {
				org.apache.commons.io.FileUtils.forceDelete(destinationFile);
			}
			org.apache.commons.io.FileUtils.moveFile(org.apache.commons.io.FileUtils.getFile(GzipUtils.getUncompressedFilename(tempDestinationFile.getCanonicalPath())),
													 destinationFile);

			// lets cleanup after ourselves - remove compressed file
			tempDestinationFile.delete();
		}
		// uncompressed file, download directry to urlDestination
		else {
			if (LOG.isInfoEnabled()) {
				LOG.info("downloadFile(), " + urlSource + ", this may take a while...");
			}
			org.apache.commons.io.FileUtils.copyURLToFile(source,
														  org.apache.commons.io.FileUtils.getFile(destination.getFile()));
		}
	}

	/**
	 * Returns a line iterator over the given file.
	 *
	 * @param urlFile String
	 * @throws Exception
	 */
	@Override
	public LineIterator getFileContents(String urlFile) throws Exception {
		return org.apache.commons.io.FileUtils.lineIterator(new File(new URL(urlFile).getFile()));
	}

	/**
	 * Method which writes the cancer study metadata file.
	 *
     * @param portalMetadata PortalMetadata
	 * @param cancerStudyMetadata CancerStudyMetadata
	 * @param numCases int
	 * @throws Exception
	 *
	 */
	@Override
	public void writeCancerStudyMetadataFile(PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata, int numCases) throws Exception {

			File metaFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																	cancerStudyMetadata.getStudyPath(),
																	cancerStudyMetadata.getCancerStudyMetadataFilename());
			if (LOG.isInfoEnabled()) {
				LOG.info("writeMetadataFile(), meta file: " + metaFile);
			}
			PrintWriter writer = new PrintWriter(org.apache.commons.io.FileUtils.openOutputStream(metaFile, false));
			writer.print("type_of_cancer: " + cancerStudyMetadata.getTumorType() + "\n");
			writer.print("cancer_study_identifier: " + cancerStudyMetadata + "\n");
			String name = (cancerStudyMetadata.getName().length() > 0) ?
				cancerStudyMetadata.getName() : cancerStudyMetadata.getTumorTypeMetadata().getName();
			name = name.replaceAll(CancerStudyMetadata.TUMOR_TYPE_NAME_TAG,
								   cancerStudyMetadata.getTumorTypeMetadata().getName());
			writer.print("name: " + name + "\n");
			String description = cancerStudyMetadata.getDescription();
			description = description.replaceAll(CancerStudyMetadata.NUM_CASES_TAG, Integer.toString(numCases));
			description = description.replaceAll(CancerStudyMetadata.TUMOR_TYPE_TAG,
												 cancerStudyMetadata.getTumorTypeMetadata().getType());
			description = description.replaceAll(CancerStudyMetadata.TUMOR_TYPE_NAME_TAG,
												 cancerStudyMetadata.getTumorTypeMetadata().getName());
			writer.print("description: " + description + "\n");
			if (cancerStudyMetadata.getCitation().length() > 0) {
				writer.print("citation: " + cancerStudyMetadata.getCitation() + "\n");
			}
			if (cancerStudyMetadata.getPMID().length() > 0) {
				writer.print("pmid: " + cancerStudyMetadata.getPMID() + "\n");
			}

			writer.flush();
			writer.close();
	}

	/**
	 * Method which writes a metadata file for the
	 * given DatatypeMetadata.  DataMatrix may be null.
	 *
     * @param portalMetadata PortalMetadata
	 * @param cancerStudyMetadata CancerStudyMetadata
	 * @param datatypeMetadata DatatypeMetadata
	 * @param dataMatrix DataMatrix
	 * @throws Exception
	 *
	 */
	@Override
	public void writeMetadataFile(PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata,
								   DatatypeMetadata datatypeMetadata, DataMatrix dataMatrix) throws Exception {

			File metaFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																	cancerStudyMetadata.getStudyPath(),
																	datatypeMetadata.getMetaFilename());
			if (LOG.isInfoEnabled()) {
				LOG.info("writeMetadataFile(), meta file: " + metaFile);
			}
			PrintWriter writer = new PrintWriter(org.apache.commons.io.FileUtils.openOutputStream(metaFile, false));
			writer.print("cancer_study_identifier: " + cancerStudyMetadata + "\n");
			writer.print("genetic_alteration_type: " + datatypeMetadata.getMetaGeneticAlterationType() + "\n");
			String stableID = datatypeMetadata.getMetaStableID();
			stableID = stableID.replaceAll(DatatypeMetadata.CANCER_STUDY_TAG, cancerStudyMetadata.toString());
			writer.print("stable_id: " + stableID + "\n");
			writer.print("show_profile_in_analysis_tab: " + datatypeMetadata.getMetaShowProfileInAnalysisTab() + "\n");
			String profileDescription = datatypeMetadata.getMetaProfileDescription();
			if (dataMatrix != null) {
				profileDescription = profileDescription.replaceAll(DatatypeMetadata.NUM_GENES_TAG, Integer.toString(dataMatrix.getGeneIDs().size()));
				profileDescription = profileDescription.replaceAll(DatatypeMetadata.NUM_CASES_TAG, Integer.toString(dataMatrix.getCaseIDs().size()));
			}
			profileDescription = profileDescription.replaceAll(DatatypeMetadata.TUMOR_TYPE_TAG, cancerStudyMetadata.getTumorType());
			writer.print("profile_description: " + profileDescription + "\n");
			writer.print("profile_name: " + datatypeMetadata.getMetaProfileName() + "\n");
			writer.flush();
			writer.close();
	}

	/**
	 * Creates a staging file with contents from the given DataMatrix.
	 *
     * @param portalMetadata PortalMetadata
	 * @param cancerStudyMetadata CancerStudyMetadata
	 * @param datatypeMetadata DatatypeMetadata
	 * @param dataMatrix DataMatrix
	 * @throws Exception
	 */
	@Override
	public void writeStagingFile(PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata,
								 DatatypeMetadata datatypeMetadata, DataMatrix dataMatrix) throws Exception {

		// staging file
		String stagingFilename = datatypeMetadata.getStagingFilename();
		stagingFilename = stagingFilename.replaceAll(DatatypeMetadata.CANCER_STUDY_TAG, cancerStudyMetadata.toString());
		File stagingFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																   cancerStudyMetadata.getStudyPath(),
																   stagingFilename);

		if (LOG.isInfoEnabled()) {
			LOG.info("writingStagingFile(), staging file: " + stagingFile);
		}
																   
		FileOutputStream out = org.apache.commons.io.FileUtils.openOutputStream(stagingFile, false);
		dataMatrix.write(out);
		IOUtils.closeQuietly(out);

		// meta file
		if (datatypeMetadata.requiresMetafile()) {
			if (LOG.isInfoEnabled()) {
				LOG.info("writingStagingFile(), creating metadata file for staging file: " + stagingFile);
			}
			writeMetadataFile(portalMetadata, cancerStudyMetadata, datatypeMetadata, dataMatrix);
		}
	}

	/**
	 * Creates a staging file for mutation data (and meta file) with contents from the given DataMatrix.
	 * This is called when the mutation file needs to be run through the Oncotator and Mutation Assessor Tools.
	 *
     * @param portalMetadata PortalMetadata
	 * @param cancerStudy CancerStudyMetadata
	 * @param datatypeMetadata DatatypeMetadata
	 * @param dataMatrix DataMatrix
	 * @throws Exception
	 */
	@Override
	public void writeMutationStagingFile(PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata,
										 DatatypeMetadata datatypeMetadata, DataMatrix dataMatrix) throws Exception {

		// we only have data matrix at this point, we need to create a temp with its contents
		File oncotatorInputFile =
			org.apache.commons.io.FileUtils.getFile(org.apache.commons.io.FileUtils.getTempDirectory(),
													""+System.currentTimeMillis()+".oncotatorInputFile");
		FileOutputStream out = org.apache.commons.io.FileUtils.openOutputStream(oncotatorInputFile);
		dataMatrix.write(out);
		IOUtils.closeQuietly(out);

		// output should be the path/name of staging file
		String stagingFilename = datatypeMetadata.getStagingFilename();
		stagingFilename = stagingFilename.replaceAll(DatatypeMetadata.CANCER_STUDY_TAG, cancerStudyMetadata.toString());
		File stagingFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																   cancerStudyMetadata.getStudyPath(),
																   stagingFilename);

		// call oncotateAF
		oncotateMAF(FileUtils.FILE_URL_PREFIX + oncotatorInputFile.getCanonicalPath(),
					FileUtils.FILE_URL_PREFIX + stagingFile.getCanonicalPath());

		// clean up
		if (oncotatorInputFile.exists()) {
			org.apache.commons.io.FileUtils.forceDelete(oncotatorInputFile);
		}

		// meta file
		if (datatypeMetadata.requiresMetafile()) {
			if (LOG.isInfoEnabled()) {
				LOG.info("writingMutationStagingFile(), creating metadata file for staging file: " + stagingFile);
			}
			writeMetadataFile(portalMetadata, cancerStudyMetadata, datatypeMetadata, dataMatrix);
		}
	}

	/**
	 * Creates a z-score staging file from the given dependencies.  It assumes that the
	 * dependency - staging files have already been created.  This code also assumes
	 * that the dependencies are ordered by cna, then expression.
	 *
     * @param portalMetadata PortalMetadata
	 * @param cancerStudy CancerStudyMetadata
	 * @param datatypeMetadata DatatypeMetadata
	 * @param dependencies DatatypeMetadata[]
	 * @throws Exception
	 */
	@Override
	public void writeZScoresStagingFile(PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata,
										DatatypeMetadata datatypeMetadata, DatatypeMetadata[] dependencies) throws Exception {

		// sanity check
		if (dependencies.length != 2) {
			throw new IllegalArgumentException("writeZScoresStagingFile(), datatypeMetadatas.length != 2, aborting...");
		}

		// check for existence of dependencies
		if (LOG.isInfoEnabled()) {
			LOG.info("writeZScoresStagingFile(), checking for existence of dependencies: " + Arrays.asList(dependencies));
		}
		File cnaFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
															   cancerStudyMetadata.getStudyPath(),
															   dependencies[0].getStagingFilename());
		if (!cnaFile.exists()) {
			if (LOG.isInfoEnabled()) {
				LOG.info("writeZScoresStagingFile(), cannot find cna file dependency: " + cnaFile.getCanonicalPath());
			}
			return;
		}

		File expressionFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																	  cancerStudyMetadata.getStudyPath(),
																	  dependencies[1].getStagingFilename());
		if (!expressionFile.exists()) { 
			if (LOG.isInfoEnabled()) {
				LOG.info("writeZScoresStagingFile(), cannot find expression file dependency: " + expressionFile.getCanonicalPath());
			}
			return;
		}

		// we need a zscore file
		File zScoresFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																   cancerStudyMetadata.getStudyPath(),
																   datatypeMetadata.getStagingFilename());
		
		// call NormalizeExpressionLevels
		String[] args = { cnaFile.getCanonicalPath(), expressionFile.getCanonicalPath(), zScoresFile.getCanonicalPath() };
		if (LOG.isInfoEnabled()) {
			LOG.info("writingZScoresStagingFlie(), calling NormalizeExpressionLevels: " + Arrays.toString(args));
		}
		try {
			NormalizeExpressionLevels.driver(args);
		}
		catch (RuntimeException e) {
			// houston we have a problem...
			if (LOG.isInfoEnabled()) {
				LOG.info("writingZScoresStagingFlie(), exception thrown by NormalizeExpressionLevels: " +
						 e.getMessage() + ", aborting...");
			}
			if (zScoresFile.exists()) {
				org.apache.commons.io.FileUtils.forceDelete(zScoresFile);
			}
			return;
		}
		
		// meta file
		if (datatypeMetadata.requiresMetafile()) {
			if (LOG.isInfoEnabled()) {
				LOG.info("writingZScoresStagingFile(), creating metadata file for staging file: " + zScoresFile.getCanonicalPath());
			}
			writeMetadataFile(portalMetadata, cancerStudyMetadata, datatypeMetadata, null);
		}
	}

	/**
	 * Returns an override file (if it exists) for the given portal & cancer study.  The override in this case
	 * is the override file that a DataMatrix is created from.
	 *
	 * Null is returned if an override file is not found.
	 *
	 * @param portalMetadata PortalMetadata
	 * @param cancerStudyMetadata CancerStudyMetadata
	 * @param filename String
	 * @return File
	 * @throws Exception
	 */
	@Override
	public File getOverrideFile(PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata, String filename) throws Exception {

		File overrideFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getOverrideDirectory(),
																	cancerStudyMetadata.getStudyPath(),
																	filename);
		return (overrideFile.exists()) ? overrideFile : null;
	}

	/**
	 * If it exists, moves an override file into the proper
	 * location in the given portals staging area
	 *
	 * @param portalMetadata PortalMetadata
	 * @param cancerStudyMetadata CancerStudyMetadata
	 * @param overrideFilename String
	 * @param stagingFilename String
	 * @throws Exception
	 */
	@Override
	public void applyOverride(PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata,
							  String overrideFilename, String stagingFilename) throws Exception {

		// check for override file
		File overrideFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getOverrideDirectory(),
																	cancerStudyMetadata.getStudyPath(),
																	overrideFilename);
		if (overrideFile.exists()) {
			File stagingFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																	   cancerStudyMetadata.getStudyPath(),
																	   stagingFilename);

			if (LOG.isInfoEnabled()) {
				LOG.info("applyOverride(), override file exists for " + stagingFile.getCanonicalPath() + ": " + 
						 overrideFile.getCanonicalPath());
			}

			// copy override file to staging area
			if (overrideFile.isFile()) {
				org.apache.commons.io.FileUtils.copyFile(overrideFile, stagingFile);
			}
			else {
				org.apache.commons.io.FileUtils.copyDirectory(overrideFile, stagingFile);
			}
		}
	}

	/**
	 * Create a case list file from the given case list metadata file.
	 *
     * @param portalMetadata PortalMetadata
	 * @param cancerStudyMetadata CancerStudyMetadata
	 * @param caseListMetadata CaseListMetadata
	 * @param caseList String[]
	 * @throws Exception
	 */
	@Override
	public void writeCaseListFile(PortalMetadata portalMetadata, CancerStudyMetadata cancerStudyMetadata, CaseListMetadata caseListMetadata, String[] caseList) throws Exception {

		File caseListFile = org.apache.commons.io.FileUtils.getFile(portalMetadata.getStagingDirectory(),
																	cancerStudyMetadata.getStudyPath(),
																	"case_lists",
																	caseListMetadata.getCaseListFilename());

		if (LOG.isInfoEnabled()) {
			LOG.info("writeCaseListFile(), case list file: " + caseListFile.getCanonicalPath());
		}
		PrintWriter writer = new PrintWriter(org.apache.commons.io.FileUtils.openOutputStream(caseListFile, false));
		writer.print("cancer_study_identifier: " + cancerStudyMetadata + "\n");
		String stableID = caseListMetadata.getMetaStableID();
		stableID = stableID.replaceAll(DatatypeMetadata.CANCER_STUDY_TAG, cancerStudyMetadata.toString());
		writer.print("stable_id: " + stableID + "\n");
		writer.print("case_list_name: " + caseListMetadata.getMetaCaseListName() + "\n");
		String caseListDescription = caseListMetadata.getMetaCaseListDescription();
		caseListDescription = caseListDescription.replaceAll(DatatypeMetadata.NUM_CASES_TAG, Integer.toString(caseList.length));
		writer.print("case_list_description: " + caseListDescription + "\n");
		writer.print("case_list_category: " + caseListMetadata.getMetaCaseListCategory() + "\n");
		writer.print("case_list_ids: ");
		for (String caseID : caseList) {
			writer.print(caseID + Converter.VALUE_DELIMITER);
		}
		writer.println();
		writer.flush();
		writer.close();
	}

	/**
	 * Runs all MAFs for the given dataaSourcesMetadata through
	 * the Oncotator and OMA tools.
	 *
	 * @param dataSourcesMetadata DataSourcesMetadata
	 * @throws Exception
	 */
	@Override
	public void oncotateAllMAFs(DataSourcesMetadata dataSourcesMetadata) throws Exception {

		// iterate over datasource download directory and process all MAFs
		String[] extensions = new String[] { DatatypeMetadata.MAF_FILE_EXT };
		for (File maf : listFiles(new File(dataSourcesMetadata.getDownloadDirectory()), extensions, true)) {
			// create temp for given maf
			File oncotatorInputFile =
				org.apache.commons.io.FileUtils.getFile(org.apache.commons.io.FileUtils.getTempDirectory(),
														""+System.currentTimeMillis()+".oncotatorInputFile");
			org.apache.commons.io.FileUtils.copyFile(maf, oncotatorInputFile);
			// input is tmp file we just created, we want output to go into the original maf
			oncotateMAF(FileUtils.FILE_URL_PREFIX + oncotatorInputFile.getCanonicalPath(),
						FileUtils.FILE_URL_PREFIX + maf.getCanonicalPath());
			// clean up
			org.apache.commons.io.FileUtils.forceDelete(oncotatorInputFile);
		}
	}

	/**
	 * Runs a MAF file through the Oncotator and OMA tools.
	 *
	 * @param inputMAFURL String
	 * @param outputMAFURL String
	 * @throws Exception 
	 */
	@Override
	public void oncotateMAF(String inputMAFURL, String outputMAFURL) throws Exception {

		// sanity check
		if (inputMAFURL == null || inputMAFURL.length() == 0 ||
			outputMAFURL == null || outputMAFURL.length() == 0) {
			throw new IllegalArgumentException("oncotateMAFdownloadFile(): url or urlDestination argument is null...");
		}

		URL inputMAF = new URL(inputMAFURL);
		URL outputMAF = new URL(outputMAFURL);

		// determine if we have to call liftover
		boolean cleanOncotatorInputFile = false;
		File oncotatorInputFile = new File(inputMAF.getFile());
		org.apache.commons.io.LineIterator it = org.apache.commons.io.FileUtils.lineIterator(oncotatorInputFile);
		it.nextLine(); // skip header
		String[] parts = it.nextLine().split("\t");
		if (parts[3].contains("36") || parts[3].equals("hg18")) {
			it.close();
			File liftoverInputFile = org.apache.commons.io.FileUtils.getFile(org.apache.commons.io.FileUtils.getTempDirectory(),
																			 ""+System.currentTimeMillis()+".liftoverInputFile");
			org.apache.commons.io.FileUtils.copyFile(oncotatorInputFile, liftoverInputFile);
			oncotatorInputFile = new File(inputMAF.getFile());
			// call lift over
			if (LOG.isInfoEnabled()) {
				LOG.info("oncotateMAF(), calling Hg18ToHg19...");
			}
			Hg18ToHg19.driver(liftoverInputFile.getCanonicalPath(), oncotatorInputFile.getCanonicalPath(), getLiftOverBinary(), getLiftOverChain());
			org.apache.commons.io.FileUtils.forceDelete(liftoverInputFile);
			cleanOncotatorInputFile = true;
		}

		// create a temp output file from the oncotator
		File oncotatorOutputFile = 
			org.apache.commons.io.FileUtils.getFile(org.apache.commons.io.FileUtils.getTempDirectory(),
													""+System.currentTimeMillis()+".oncotatorOutputFile");
		// call oncotator
		if (LOG.isInfoEnabled()) {
			LOG.info("oncotateMAF(), calling OncotateTool...");
		}
		OncotateTool.driver(oncotatorInputFile.getCanonicalPath(), oncotatorOutputFile.getCanonicalPath(), true, true, true);
		// we call OMA here -
		// we use output from oncotator as input file
		if (LOG.isInfoEnabled()) {
			LOG.info("oncotateMAF(), calling MutationAssessorTool...");
		}
		File outputMAFFile = new File(outputMAF.getFile());
		outputMAFFile.createNewFile();
		MutationAssessorTool.driver(oncotatorOutputFile.getCanonicalPath(), outputMAFFile.getCanonicalPath(), false, true, true);

		// clean up
		org.apache.commons.io.FileUtils.forceDelete(oncotatorOutputFile);
		if (cleanOncotatorInputFile) org.apache.commons.io.FileUtils.forceDelete(oncotatorInputFile);
	}

	/**
	 * Copy's the given portal's seg files to location used for linking to IGV from cBio Portal web site.
	 *
	 * @param portalMetadata PortalMetadata
	 * @param datatypeMetadata DatataypeMetadata
	 * @param remoteUserName String
	 * @throws Exception
	 */
	@Override
	public void copySegFiles(PortalMetadata portalMetadata, DatatypeMetadata datatypeMetadata, String remoteUserName) throws Exception {

		if (LOG.isInfoEnabled()) {
			LOG.info("copySegFiles()");
		}

        // check args
        if (portalMetadata == null || remoteUserName == null) {
            throw new IllegalArgumentException("portal or remoteUserName must not be null");
		}

		// seg file location
		URL segFileLocation = portalMetadata.getIGVSegFileLinkingLocation();

		// we need this to determine location 
		Collection<DataSourcesMetadata> dataSourcesMetadata = config.getDataSourcesMetadata(Config.ALL);

		// iterate over all cancer studies
		for (CancerStudyMetadata cancerStudyMetadata : config.getCancerStudyMetadata(portalMetadata.getName())) {

			// lets determine if cancer study is in staging directory or studies directory
			String rootDirectory = MetadataUtils.getCancerStudyRootDirectory(portalMetadata, dataSourcesMetadata, cancerStudyMetadata);

			if (rootDirectory == null) {
				if (LOG.isInfoEnabled()) {
					LOG.info("loadStagingFiles(), cannot find root directory for study: " + cancerStudyMetadata + " skipping...");
				}
				continue;
			}

			// construct staging filename for seg
			String sourceFilename = (rootDirectory + File.separator +
									  cancerStudyMetadata.getStudyPath() +
									  File.separator + datatypeMetadata.getStagingFilename());
			sourceFilename = sourceFilename.replaceAll(DatatypeMetadata.CANCER_STUDY_TAG, cancerStudyMetadata.toString());
			String destinationFilename = datatypeMetadata.getStagingFilename().replaceAll(DatatypeMetadata.CANCER_STUDY_TAG, cancerStudyMetadata.toString());

			String[] command = new String[] { "scp",
											  sourceFilename,
											  remoteUserName + "@" + segFileLocation.getHost() + ":" +
											  segFileLocation.getFile() + destinationFilename };
			if (LOG.isInfoEnabled()) {
				LOG.info("executing: " + Arrays.asList(command));
			}
			if (Shell.exec(Arrays.asList(command), ".")) {
				if (LOG.isInfoEnabled()) {
					LOG.info("copy successful.");
				}
			}
			else if (LOG.isInfoEnabled()) {
				LOG.info("copy unsucessful.");
			}
		}
	}

    /*
     * Given a zip stream, unzips it and returns an input stream to the desired data file.
     *
     * @param importDataRecord ImportDataRecord
     * @param is InputStream
     * @return InputStream
     */
    private InputStream readContent(ImportDataRecord importDataRecord, InputStream is) throws Exception {

        InputStream toReturn = null;

        try {
            // decompress .gz file
            if (LOG.isInfoEnabled()) {
                LOG.info("readContent(), decompressing: " + importDataRecord.getCanonicalPathToData());
            }

            InputStream unzippedContent = new GzipCompressorInputStream(is);
            // if tarball, untar
            if (importDataRecord.getCanonicalPathToData().toLowerCase().endsWith("tar.gz")) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("readContent(), gzip file is a tarball, untarring");
                }
                TarArchiveInputStream tis = new TarArchiveInputStream(unzippedContent);
                TarArchiveEntry entry = null;
                while ((entry = tis.getNextTarEntry()) != null) {
                    String entryName = entry.getName();
                    String dataFile = importDataRecord.getDataFilename();
                    if (dataFile.contains(DatatypeMetadata.TUMOR_TYPE_TAG)) {
                        dataFile = dataFile.replaceAll(DatatypeMetadata.TUMOR_TYPE_TAG, importDataRecord.getTumorTypeLabel());
                    }
                    if (entryName.contains(dataFile)) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("Processing tar-archive: " + importDataRecord.getDataFilename());
                        }
                        toReturn = tis;
                        break;
                    }
                }
            }
            else {
                toReturn = unzippedContent;
            }
        }
        catch (Exception e) {
            throw e;
        }
        
        // outta here
        return toReturn;
    }

    /**
     * Helper function to create DataMatrix.
     *
     * @param data InputStream
	 * @param methylationCorrelation DataMatrix
     * @return DataMatrix
     */
    private DataMatrix getDataMatrix(InputStream data, DataMatrix methylationCorrelation) throws Exception {

        // iterate over all lines in byte[]
        List<String> columnNames = null;
        List<LinkedList<String>> rowData = null;
        LineIterator it = IOUtils.lineIterator(data, null);
		Map<String,String> probeIdMap = initProbMap(methylationCorrelation);
        try {
            int count = -1;
            while (it.hasNext()) {
                // first row is our column heading, create column vector
                if (++count == 0) {
                    columnNames = new LinkedList(Arrays.asList(it.nextLine().split(Converter.VALUE_DELIMITER, -1)));
                }
                // all other rows are rows in the table
                else {
                    rowData = (rowData == null) ? new LinkedList<LinkedList<String>>() : rowData;
					LinkedList thisRow = new LinkedList(Arrays.asList(it.nextLine().split(Converter.VALUE_DELIMITER, -1)));
					if (methylationCorrelation == null) {
						rowData.add(thisRow);
					}
					// first line in methylation file is probeID
					else if (probeIdMap.containsKey(thisRow.getFirst())) {
						rowData.add(thisRow);
					}
                }
            }
        }
        finally {
            LineIterator.closeQuietly(it);
        }

        // problem reading from data?
        if (columnNames == null || rowData == null) {
            if (LOG.isInfoEnabled()) {
                LOG.info("getDataMatrix(), problem creating DataMatrix from file, data file probably missing data, returning null");
            }
            return null;
        }

        // made it here, we can create DataMatrix
        if (LOG.isInfoEnabled()) {
            LOG.info("creating new DataMatrix(), from file data, num rows: " + rowData.size());
        }

        // outta here
        return new DataMatrix(rowData, columnNames);
    }

	/**
	 * Helper function to gunzip file.  gzipFile param is canonical path.
	 *
	 * @param gzipFile String
	 */
	private static void gunzip(String gzipFile) throws Exception {

		// setup our gzip inputs tream
		FileOutputStream fos = null;
		String outFilePath = GzipUtils.getUncompressedFilename(gzipFile);
		GZIPInputStream gis = new GZIPInputStream(new FileInputStream(gzipFile));
 
		try {
			// unzip into file less the .gz
			fos = new FileOutputStream(outFilePath);
			IOUtils.copy(gis, fos);
		}
		finally {
			// close up our streams
			IOUtils.closeQuietly(gis);
			if (fos != null) IOUtils.closeQuietly(fos);
		}
 	}

	private static Map initProbMap(DataMatrix methylationCorrelation) {
		Map<String,String> toReturn = new HashMap<String,String>();
		if (methylationCorrelation == null) return toReturn;
		for (String probeId : methylationCorrelation.
				 getColumnData(MethylationConverterImpl.CORRELATE_METH_PROBE_COLUMN_HEADER_NAME).get(0)) {
			toReturn.put(probeId, probeId);
		}
		return toReturn;
	}
}