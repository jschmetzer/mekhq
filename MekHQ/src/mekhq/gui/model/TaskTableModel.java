package mekhq.gui.model;

import java.awt.Component;
import java.awt.Image;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import megamek.common.TargetRoll;
import mekhq.IconPackage;
import mekhq.campaign.parts.MissingPart;
import mekhq.campaign.parts.Part;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.Skill;
import mekhq.gui.CampaignGUI;
import mekhq.gui.RepairTaskInfo;

/**
 * A table model for displaying work items
 */
public class TaskTableModel extends DataTableModel {
    private static final long serialVersionUID = -6256038046416893994L;
    private static Map<String, Person> techCache = new HashMap<String, Person>();
    
    private CampaignGUI campaignGUI;
    private JTable techTable;
    
    private interface REPAIR_STATE {
    	public static final int AVAILABLE = 0;
    	public static final int NOT_AVAILABLE = 1;
    	public static final int IN_TRANSIT = 2;
    	public static final int BLOCKED = 3;
    	public static final int SCHEDULED = 4;
    }
    
    public TaskTableModel(CampaignGUI campaignGUI, JTable techTable) {
        columnNames = new String[] { "Tasks" };
        data = new ArrayList<Part>();
        
        this.campaignGUI = campaignGUI;
        this.techTable = techTable;
    }

    public Object getValueAt(int row, int col) {
        return ((Part) data.get(row)).getDesc();
    }

    public Part getTaskAt(int row) {
        return (Part) data.get(row);
    }

    public Part[] getTasksAt(int[] rows) {
        Part[] tasks = new Part[rows.length];
        for (int i = 0; i < rows.length; i++) {
            int row = rows[i];
            tasks[i] = (Part) data.get(row);
        }
        return tasks;
    }

    public Person getSelectedTech() {
        int row = techTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return ((TechTableModel)techTable.getModel()).getTechAt(techTable.convertRowIndexToModel(row));
    }

    public TaskTableModel.Renderer getRenderer(IconPackage icons) {
        return new TaskTableModel.Renderer(icons);
    }

    public class Renderer extends RepairTaskInfo implements TableCellRenderer {

        public Renderer(IconPackage icons) {
            super(icons);
        }

        private static final long serialVersionUID = -3052618135259621130L;

        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            Component c = this;
            int actualCol = table.convertColumnIndexToModel(column);
            int actualRow = table.convertRowIndexToModel(row);
            
            setOpaque(true);
            setText("<html>" + getValueAt(actualRow, actualCol).toString() + "</html>", "black");
            if (isSelected) {
                highlightBorder();
            } else {
                unhighlightBorder();
            }
            
            Part part = getTaskAt(actualRow);
            
            int availableLevel = REPAIR_STATE.AVAILABLE;
            
            if (null != part.getTeamId()) {
            	availableLevel = REPAIR_STATE.SCHEDULED;
            } else {            	
            	if (part instanceof MissingPart) {
            		if (!((MissingPart)part).isReplacementAvailable()) {
	            		String[] inventories = campaignGUI.getCampaign().getPartInventory(((MissingPart) part).getNewPart());
	            		
	            		//int inStock = processInventoryString(inventories[0]);
	            		int inTransit = processInventoryString(inventories[1]);
	            		int onOrder = processInventoryString(inventories[2]);
	            		
	            		if ((inTransit > 0) || (onOrder > 0)) {
	            			availableLevel = REPAIR_STATE.IN_TRANSIT;
	            		} else {
	            			availableLevel = REPAIR_STATE.NOT_AVAILABLE;
	            		}
            		}
            	}
            	
            	if (availableLevel == REPAIR_STATE.AVAILABLE) {
	                Person tech = getSelectedTech();
	                
	                if (null == tech) {
	                	//Find a valid tech that we can copy their skill from
	                	ArrayList<Person> techs = campaignGUI.getCampaign().getTechs(false);
	                	
	        			for (int i = techs.size() - 1; i >= 0; i--) {
	        				Person techTemp = techs.get(i);

	        				if (techTemp.canTech(part.getUnit().getEntity())) {
	        					tech = techTemp;
	        					break;
	        				}
	        			}
	        			
	        			if (null != tech) {
		        			Skill partSkill = tech.getSkillForWorkingOn(part);
		        			String skillName = partSkill.getType().getName();
		        			
	        				//Find a tech in our placeholder cache
	        				tech = techCache.get(skillName);
	        				
	        				if (null == tech) {
			        			//Create a dummy elite tech with the proper skill and 1 minute and put it in our cache for later use
			        			
			        			tech = new Person(String.format("Temp Tech (%s)", skillName), campaignGUI.getCampaign());
			        			tech.addSkill(skillName, partSkill.getType().getEliteLevel(), 1);
			        			tech.setMinutesLeft(1);
			        			
			        			techCache.put(skillName, tech);
	        				}
	        			}
	                }
	                
	                if (null != tech) {
	                	TargetRoll roll = campaignGUI.getCampaign().getTargetFor(part, tech);
	                	
	                	if ((roll.getValue() == TargetRoll.IMPOSSIBLE) || (roll.getValue() == TargetRoll.AUTOMATIC_FAIL) || (roll.getValue() == TargetRoll.CHECK_FALSE)) {
	                		availableLevel = REPAIR_STATE.BLOCKED;
	                	}
	                }
            	}
            }
            
            String imgMod = "";
            boolean setSecondary = false;
            
            switch (availableLevel) {
	            case REPAIR_STATE.BLOCKED:
	            	imgMod = "_impossible";	            	
	            	break;
	            	
	            case REPAIR_STATE.IN_TRANSIT:
	            	imgMod = "_transit";	            	
	            	break;
	            	
	            case REPAIR_STATE.NOT_AVAILABLE:
	            	imgMod = "_na";	            	
	            	break;
	            	
	            case REPAIR_STATE.SCHEDULED:
	            	setSecondary = true;
	            	break;
            }

        	String[] imgData = Part.findPartImage(part);
        	String imgPath = imgData[0] + imgData[1] + imgMod + ".png";
            
            Image imgTool = getToolkit().getImage(imgPath); //$NON-NLS-1$
            
            this.setImage(imgTool);
            
            if (setSecondary) {
            	this.setSecondaryImage(getToolkit().getImage("data/images/misc/repair/working.png"));	
            } else {
            	this.setSecondaryImage(null);
            }
            
            return c;
        }

        //This is a hack to compensate for the sub-optimal return values
		private int processInventoryString(String str) {
			if (str.indexOf(" ") > -1) {
				return Integer.parseInt(str.substring(0, str.indexOf(" ")));
			}

			return Integer.parseInt(str);
		}
    }
}