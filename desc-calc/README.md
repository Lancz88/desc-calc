# DESCENT CALCULATOR

Generating descent profile and step by step itinerant for easing manual descending.
Useful if you don't want to, or you can't rely on aircraft's managed autopilot mode.

## Usage

- Paste waypoint information including constraints and distances in CSV format into
  **waypoints-paste.csv** file. Information needs to be precise, but you don't have to contain
the full flight plan, only the waypoints where descending expected.
- Run app with gradlew command from terminal (from withing app directory).
- Example for command with arguments (see them explained below): **gradlew bootRun --args="37000 294 435 425 0.6"**

### Input arguments
Arguments should be passed in order, if skipping parameter don't add other ones after.
- Initial altitude / crz altitude. Required
- Initial speed. Optional
- TS speed. For wind adjustments. Optional
- GS ground speed. For wind adjustments. Optional
- Wind - alt ratio, between 0 and 1. Wind relevance ratio between altitudes. Optional

## Waypoint information required
- **idx** - Ordering starts from runway. Required
- **name** - Waypoint name. Required
- **distanceToNext** - Distance in miles to next waypoint to runway direction (or to the rwy). Required
- **altHold** - Exact alt constraint restriction. Optional
- **altAbove** - Minimum alt constraint requirement. Optional
- **altBelow** - Maximum alt constraint requirement. Optional
- **speed** - Max speed allowed, and/or suggested speed. Optional

## Understand result
The final action chain printed follows the following format:

**T/D  --  WAYPT + X  --  DESCEND BY 3.0**

The result always stars with top of descend information. WAYPT + X indicates that you should start descending X miles before waypoint named WAYPT. This is followed by the ACTION, which is always the same this case, telling that start descending by 3.0 FPA.

**WAYPT (X)  --  ACTION**

The upcoming lines follow the format above. WAYPT is the name of the waypoint, X is the total distance in miles from the runway, ACTION means the action that has to be executed at this waypoint (see them explained below). If waypoint is not presented in the result, then you don't need to do any action there.

**Understand ACTION variations:**
- **DESCEND BY X.Y** - Start descending by FPA x.y from the actual waypoint. **Keep this descending value** until next DESCEND BY or ALT actions.
- **ALT X** - Stop descending and keep altitude X.
- **HOLD X** - Similar to ALT, you should be exactly at altitude X at the actual waypoint, but **don't keep it**, continue previous descending rate right after passing the waypoint.
- **HOLD ABOVE X or HOLD BELOW X** - Keep descending, the descend path goes close to a constraint, be careful keeping above or below X at this point, alter descend rate temporarily if needed.
- **DECEL TO X** - Decelerate to speed X **before** reaching the waypoint and hold that speed from the waypoint.

## Best Practices

- Best to copy the waypoint information directly fom aircraft FMS.
- Use Excel to store the data first for better visibility, and generate the .csv from there.

### If descend plan still complex or too tight
- Move DECEL points earlier - remove some, simplify.
- Place new **altHold** where **altAbove** and **altBelow** are too close to each other.
- Remove constraints that can be passed easily to clean up final result - validate if they are still passing in the logs.
- Regenerate result a couple of times to see how it evolves.

Good flight!:)