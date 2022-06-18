/*
 * Copyright (c) 2020-2022 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.campaign.personnel.familyTree;

import megamek.common.annotations.Nullable;
import megamek.common.enums.Gender;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.enums.FamilialRelationshipType;
import mekhq.io.idReferenceClasses.PersonIdReference;
import mekhq.utilities.MHQXMLUtility;
import org.apache.logging.log4j.LogManager;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The Genealogy class is used to track immediate familial relationships, spouses, and former spouses.
 * It is also used to determine familial relationships between people
 */
public class Genealogy {
    //region Variables
    private final Person origin;
    private Person spouse;
    private List<FormerSpouse> formerSpouses;
    private Map<FamilialRelationshipType, List<Person>> family;
    //endregion Variables

    //region Constructors
    /**
     * This is the standard constructor, and follow the below warning
     * @param origin the origin person
     */
    public Genealogy(final Person origin) {
        this.origin = origin;
        setSpouse(null);
        setFormerSpouses(new ArrayList<>());
        setFamily(new HashMap<>());
    }
    //endregion Constructors

    //region Getters/Setters
    /**
     * @return the origin person
     */
    public Person getOrigin() {
        return origin;
    }

    /**
     * @return the current person's spouse
     */
    public @Nullable Person getSpouse() {
        return spouse;
    }

    /**
     * @param spouse the new spouse for the current person
     */
    public void setSpouse(final @Nullable Person spouse) {
        this.spouse = spouse;
    }

    /**
     * @return a list of FormerSpouse objects for all the former spouses of the current person
     */
    public List<FormerSpouse> getFormerSpouses() {
        return formerSpouses;
    }

    /**
     * @param formerSpouse a former spouse to add to the current person's list
     */
    public void addFormerSpouse(final FormerSpouse formerSpouse) {
        getFormerSpouses().add(formerSpouse);
    }

    /**
     * @param formerSpouse the former spouse object to remove from the current person's list. Do
     *                     note that this may remove multiple identical former spouses, as we do
     *                     not require uniqueness for former spouses.
     */
    public void removeFormerSpouse(final FormerSpouse formerSpouse) {
        getFormerSpouses().removeIf(ex -> ex.equals(formerSpouse));
    }

    /**
     * @param formerSpouse the former spouse to remove from the current person's list
     */
    public void removeFormerSpouse(final Person formerSpouse) {
        getFormerSpouses().removeIf(ex -> ex.getFormerSpouse().equals(formerSpouse));
    }

    public void setFormerSpouses(final List<FormerSpouse> formerSpouses) {
        this.formerSpouses = formerSpouses;
    }

    /**
     * @return the family map for this person
     */
    public Map<FamilialRelationshipType, List<Person>> getFamily() {
        return family;
    }

    /**
     * @param family the new family map for this person
     */
    public void setFamily(final Map<FamilialRelationshipType, List<Person>> family) {
        this.family = family;
    }

    /**
     * This is used to add a new family member
     * @param relationshipType the relationship type between the two people
     * @param person the person to add
     */
    public void addFamilyMember(final FamilialRelationshipType relationshipType,
                                final @Nullable Person person) {
        if (person != null) {
            getFamily().putIfAbsent(relationshipType, new ArrayList<>());
            getFamily().get(relationshipType).add(person);
        }
    }

    /**
     * @param relationshipType the FamilialRelationshipType of the person to remove
     * @param person the person to remove
     */
    public void removeFamilyMember(final @Nullable FamilialRelationshipType relationshipType,
                                   final Person person) {
        if (relationshipType == null) {
            for (FamilialRelationshipType type : FamilialRelationshipType.values()) {
                List<Person> familyMembers = getFamily().getOrDefault(type, new ArrayList<>());
                if (!familyMembers.isEmpty() && familyMembers.contains(person)) {
                    familyMembers.remove(person);
                    if (familyMembers.isEmpty()) {
                        getFamily().remove(type);
                    }
                    break;
                }
            }
        } else if (getFamily().get(relationshipType) == null) {
            LogManager.getLogger().error("Could not remove unknown family member of relationship "
                    + relationshipType.name() + " and person " + person.getFullTitle() + ' ' + person.getId() + '.');
        } else {
            List<Person> familyTypeMembers = getFamily().get(relationshipType);
            familyTypeMembers.remove(person);
            if (familyTypeMembers.isEmpty()) {
                getFamily().remove(relationshipType);
            }
        }
    }
    //endregion Getters/Setters

    //region Boolean Checks
    /**
     * @return true if the person has either a spouse, any children, or specified parents.
     *          These are required for any extended family to exist.
     */
    public boolean hasAnyFamily() {
        return hasChildren() || hasSpouse() || hasParents();
    }

    /**
     * @return true if the person has a spouse, false otherwise
     */
    public boolean hasSpouse() {
        return getSpouse() != null;
    }

    /**
     * @return true if the person has a former spouse, false otherwise
     */
    public boolean hasFormerSpouse() {
        return !getFormerSpouses().isEmpty();
    }

    /**
     * @return true if the person has at least one kid, false otherwise
     */
    public boolean hasChildren() {
        return getFamily().get(FamilialRelationshipType.CHILD) != null;
    }

    /**
     * @return true if the Person has any parents, otherwise false
     */
    public boolean hasParents() {
        return getFamily().get(FamilialRelationshipType.PARENT) != null;
    }

    /**
     * This is used to determine if two people have mutual ancestors based on their genealogies
     * @param person the person to check if they are related or not
     * @param depth the depth to check mutual ancestry up to
     * @return true if they have mutual ancestors, otherwise false
     */
    public boolean checkMutualAncestors(final Person person, final int depth) {
        if (getOrigin().equals(person)) {
            // Same person will always return true, to prevent any weirdness
            return true;
        } else if (depth == 0) {
            // Check is disabled, return false for no mutual ancestors
            return false;
        }

        final Set<Person> originAncestors = getAncestors(depth);
        return person.getGenealogy().getAncestors(depth).stream().anyMatch(originAncestors::contains);
    }

    /**
     * @param depth the depth of ancestors to get
     * @return a set of all unique ancestors of a person back depth generations
     * @note this is a recursive search to ensure it goes to a specified depth of relation
     */
    private Set<Person> getAncestors(int depth) {
        // Create the return value
        Set<Person> ancestors = new HashSet<>();

        // Add this person to the return set
        ancestors.add(getOrigin());

        // Then check if we need to continue down the tree
        if (depth > 0) {
            // If so, decrease remaining search depth
            depth--;
            // Then parse through the parents
            for (final Person parent : getParents()) {
                // And add all of their returned ancestors to the list
                ancestors.addAll(parent.getGenealogy().getAncestors(depth));
            }
        }

        // Finally, return the ancestors
        return ancestors;
    }
    //endregion Boolean Checks

    //region Basic Family Getters
    /**
     * @return a list of the current person's grandparent(s)
     */
    public List<Person> getGrandparents() {
        return getParents().stream()
                .flatMap(parent -> parent.getGenealogy().getParents().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * @return the person's parent(s)
     */
    public List<Person> getParents() {
        return getFamily().getOrDefault(FamilialRelationshipType.PARENT, new ArrayList<>());
    }

    /**
     * @param gender the gender of the parent(s) to get
     * @return a list of the person's parent(s) of the specified gender
     */
    public List<Person> getParentsByGender(final Gender gender) {
        return getFamily()
                .getOrDefault(FamilialRelationshipType.PARENT, new ArrayList<>())
                .stream()
                .filter(parent -> parent.getGender() == gender)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * @return the person's father(s)
     */
    public List<Person> getFathers() {
        return getParentsByGender(Gender.MALE);
    }

    /**
     * @return the person's mother(s)
     */
    public List<Person> getMothers() {
        return getParentsByGender(Gender.FEMALE);
    }

    /**
     * Siblings are defined as sharing either parent. Inlaws are not counted.
     * @return the siblings of the current person
     */
    public List<Person> getSiblings() {
        return getParents().stream()
                .flatMap(parent -> parent.getGenealogy().getChildren().stream())
                .distinct()
                .filter(sibling -> !getOrigin().equals(sibling))
                .collect(Collectors.toList());
    }

    /**
     * @return a list of the person's siblings with spouses (if any)
     */
    public List<Person> getSiblingsAndSpouses() {
        final List<Person> siblingsAndSpouses = new ArrayList<>();
        for (final Person sibling : getSiblings()) {
            siblingsAndSpouses.remove(sibling);
            siblingsAndSpouses.add(sibling);
            if (sibling.getGenealogy().hasSpouse()) {
                siblingsAndSpouses.remove(sibling.getGenealogy().getSpouse());
                siblingsAndSpouses.add(sibling.getGenealogy().getSpouse());
            }
        }
        return siblingsAndSpouses;
    }

    /**
     * @return a list of the current person's children
     */
    public List<Person> getChildren() {
        return getFamily().getOrDefault(FamilialRelationshipType.CHILD, new ArrayList<>());
    }

    /**
     * @return a list of the person's grandchildren
     */
    public List<Person> getGrandchildren() {
        return getChildren().stream()
                .flatMap(child -> child.getGenealogy().getChildren().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * @return a list of the person's Aunts and Uncles
     */
    public List<Person> getsAuntsAndUncles() {
        return getParents().stream()
                .flatMap(parent -> parent.getGenealogy().getSiblingsAndSpouses().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * @return a list of the person's cousins
     */
    public List<Person> getCousins() {
        return getsAuntsAndUncles().stream()
                .flatMap(auntOrUncle -> auntOrUncle.getGenealogy().getChildren().stream())
                .distinct()
                .collect(Collectors.toList());
    }
    //endregion Basic Family Getters

    //region File I/O
    /**
     * @param pw the PrintWriter to write to
     * @param indent the indent for the base line (i.e. the line containing genealogy)
     */
    public void writeToXML(final PrintWriter pw, int indent) {
        MHQXMLUtility.writeSimpleXMLOpenTag(pw, indent++, "genealogy");
        if (getSpouse() != null) {
            MHQXMLUtility.writeSimpleXMLTag(pw, indent, "spouse", getSpouse().getId());
        }

        if (!getFormerSpouses().isEmpty()) {
            MHQXMLUtility.writeSimpleXMLOpenTag(pw, indent++, "formerSpouses");
            for (FormerSpouse ex : getFormerSpouses()) {
                ex.writeToXML(pw, indent);
            }
            MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "formerSpouses");
        }

        if (!familyIsEmpty()) {
            MHQXMLUtility.writeSimpleXMLOpenTag(pw, indent++, "family");
            for (FamilialRelationshipType relationshipType : getFamily().keySet()) {
                MHQXMLUtility.writeSimpleXMLOpenTag(pw, indent++, "relationship");
                MHQXMLUtility.writeSimpleXMLTag(pw, indent, "type", relationshipType.name());
                for (Person person : getFamily().get(relationshipType)) {
                    MHQXMLUtility.writeSimpleXMLTag(pw, indent, "personId", person.getId());
                }
                MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "relationship");
            }
            MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "family");
        }
        MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "genealogy");
    }

    /**
     * @param nl the NodeList containing the saved Genealogy
     */
    public void fillFromXML(final NodeList nl) {
        for (int x = 0; x < nl.getLength(); x++) {
            final Node wn = nl.item(x);
            try {
                if (wn.getNodeName().equalsIgnoreCase("spouse")) {
                    setSpouse(new PersonIdReference(wn.getTextContent().trim()));
                } else if (wn.getNodeName().equalsIgnoreCase("formerSpouses")) {
                    if (wn.hasChildNodes()) {
                        loadFormerSpouses(wn.getChildNodes());
                    }
                } else if (wn.getNodeName().equalsIgnoreCase("family")) {
                    if (wn.hasChildNodes()) {
                        loadFamily(wn.getChildNodes());
                    }
                }
            } catch (Exception ex) {
                LogManager.getLogger().error("Failed to parse " + wn.getTextContent() + " for " + getOrigin().getId(), ex);
            }
        }
    }

    /**
     * This loads the FormerSpouses from their saved nodes
     * Note: This must be public for migration reasons
     * @param nl the NodeList containing the saved former spouses
     */
    public void loadFormerSpouses(final NodeList nl) {
        for (int y = 0; y < nl.getLength(); y++) {
            try {
                final Node wn = nl.item(y);
                // If it's not an element node, we ignore it.
                if (wn.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                if (!wn.getNodeName().equalsIgnoreCase("formerSpouse")) {
                    LogManager.getLogger().error("Unknown node type not loaded in formerSpouses nodes: "
                            + wn.getNodeName());
                    continue;
                }
                getFormerSpouses().add(FormerSpouse.generateInstanceFromXML(wn));
            } catch (Exception ex) {
                // Only skip this node, not the whole genealogy
                LogManager.getLogger().error("", ex);
            }
        }
    }

    /**
     * This loads the familial relationships from their saved nodes
     * @param nl the NodeList containing the saved Genealogy familial relationships
     */
    private void loadFamily(final NodeList nl) {
        for (int y = 0; y < nl.getLength(); y++) {
            final Node wn = nl.item(y);

            if (wn.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            if (wn.getNodeName().equalsIgnoreCase("relationship")) {
                final NodeList nl2 = wn.getChildNodes();
                // The default value should never be used, but it is useful to have a default
                FamilialRelationshipType type = FamilialRelationshipType.PARENT;
                final List<Person> people = new ArrayList<>();
                for (int i = 0; i < nl2.getLength(); i++) {
                    final Node wn2 = nl2.item(i);
                    if (wn2.getNodeName().equalsIgnoreCase("type")) {
                        type = FamilialRelationshipType.valueOf(wn2.getTextContent().trim());
                    } else if (wn2.getNodeName().equalsIgnoreCase("personId")) {
                        people.add(new PersonIdReference(wn2.getTextContent().trim()));
                    }
                }
                getFamily().put(type, people);
            }
        }
    }

    /**
     * @return whether the Genealogy object is empty or not
     */
    public boolean isEmpty() {
        return (getSpouse() == null) && getFormerSpouses().isEmpty() && familyIsEmpty();
    }

    /**
     * @return whether the family side of the Genealogy object is empty or not
     * (i.e. spouse and formerSpouses are not included, just family)
     */
    public boolean familyIsEmpty() {
        return getFamily().values().stream().noneMatch(list -> (list != null) && !list.isEmpty());
    }
    //endregion File I/O

    //region Clear Genealogy
    /**
     * This is used to remove Genealogy links to a person
     */
    public void clearGenealogy() {
        // Clear Spouse
        if (getSpouse() != null) {
            getSpouse().getGenealogy().setSpouse(null);
        }

        // Clear Former Spouses
        if (!getFormerSpouses().isEmpty()) {
            for (final FormerSpouse formerSpouse : getFormerSpouses()) {
                final Person person = formerSpouse.getFormerSpouse();
                if (person != null) {
                    person.getGenealogy().removeFormerSpouse(getOrigin());
                }
            }
        }

        // Clear Family
        if (!familyIsEmpty()) {
            for (final List<Person> list : getFamily().values()) {
                for (final Person person : list) {
                    person.getGenealogy().removeFamilyMember(null, getOrigin());
                }
            }
        }
    }
    //endregion Clear Genealogy
}
