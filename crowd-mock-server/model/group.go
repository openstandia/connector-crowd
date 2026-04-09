package model

import "encoding/xml"

// <group name="foo">
//   <description>desc</description>
//   <type>GROUP</type>
//   <active>true</active>
//   <attributes>...</attributes>
// </group>
type GroupEntity struct {
	XMLName     xml.Name       `xml:"group"`
	Name        string         `xml:"name,attr"`
	Description string         `xml:"description,omitempty"`
	Type        string         `xml:"type"`
	Active      bool           `xml:"active"`
	Attributes  *AttributeList `xml:"attributes,omitempty"`
}

type GroupRef struct {
	XMLName xml.Name `xml:"group"`
	Name    string   `xml:"name,attr"`
}

// <groups><group name="foo"/></groups>
type GroupsResponse struct {
	XMLName xml.Name   `xml:"groups"`
	Groups  []GroupRef `xml:"group"`
}

// <groups><group name="foo">...</group></groups>
type GroupEntityList struct {
	XMLName xml.Name      `xml:"groups"`
	Groups  []GroupEntity `xml:"group"`
}
