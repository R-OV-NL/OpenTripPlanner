package org.opentripplanner.netex.nl;

import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.model.impl.TransitDataImportBuilder;
import org.opentripplanner.netex.NetexBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated module for handling Dutch NeTEx Flex profile (Bravoflex etc.)
 * This runs independently of the standard Nordic NeTEx parser to ensure
 * we handle the specific "One ServiceJourney per Trip" + "Timebands" structure
 * correctly.
 */
public class NetexFlexNLModule {

    private static final Logger LOG = LoggerFactory.getLogger(NetexFlexNLModule.class);

    public void processBundle(
            NetexBundle bundle,
            TransitDataImportBuilder builder,
            DataImportIssueStore issueStore) {
        LOG.info("Processing NeTEx bundle for NL Flex profile...");

        NetexFlexNLParser parser = new NetexFlexNLParser(builder, issueStore, bundle.getFeedId());

        // 1. Process global shared files
        for (org.opentripplanner.datastore.api.DataSource source : bundle.getHierarchy().sharedEntries()) {
            processSource(source, parser, issueStore);
        }

        // 2. Process groups
        for (org.opentripplanner.netex.loader.GroupEntries group : bundle.getHierarchy().groups()) {
            for (org.opentripplanner.datastore.api.DataSource source : group.sharedEntries()) {
                processSource(source, parser, issueStore);
            }
            for (org.opentripplanner.datastore.api.DataSource source : group.independentEntries()) {
                processSource(source, parser, issueStore);
            }
        }
    }

    private void processSource(org.opentripplanner.datastore.api.DataSource source, NetexFlexNLParser parser,
            DataImportIssueStore issueStore) {
        try (java.io.InputStream in = source.asInputStream()) {
            parser.parse(in, source.name());
        } catch (Exception e) {
            LOG.error("Error parsing NeTEx file for NL Flex: {}", source.name(), e);
            issueStore.add("NetexFlexNLParseError", "Error parsing file: " + source.name(), e.toString());
        }
    }
}
